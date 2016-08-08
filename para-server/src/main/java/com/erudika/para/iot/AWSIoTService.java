/*
 * Copyright 2013-2016 Erudika. http://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.iot;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.AttachThingPrincipalRequest;
import com.amazonaws.services.iot.model.AttributePayload;
import com.amazonaws.services.iot.model.CertificateStatus;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.amazonaws.services.iot.model.CreatePolicyRequest;
import com.amazonaws.services.iot.model.CreateThingRequest;
import com.amazonaws.services.iot.model.CreateThingResult;
import com.amazonaws.services.iot.model.DeleteCertificateRequest;
import com.amazonaws.services.iot.model.DeletePolicyRequest;
import com.amazonaws.services.iot.model.DeletePolicyVersionRequest;
import com.amazonaws.services.iot.model.DeleteThingRequest;
import com.amazonaws.services.iot.model.DescribeEndpointRequest;
import com.amazonaws.services.iot.model.DescribeThingRequest;
import com.amazonaws.services.iot.model.DetachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.DetachThingPrincipalRequest;
import com.amazonaws.services.iot.model.ListPolicyVersionsRequest;
import com.amazonaws.services.iot.model.PolicyVersion;
import com.amazonaws.services.iot.model.UpdateCertificateRequest;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.model.DeleteThingShadowRequest;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.iotdata.model.UpdateThingShadowRequest;
import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWS IoT client.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSIoTService implements IoTService {

	private static AWSIotClient iotClient;
	private static AWSIotDataClient iotDataClient;
	private static final Logger logger = LoggerFactory.getLogger(AWSIoTService.class);

	public AWSIoTService() { }

	protected AWSIotClient getClient() {
		if (iotClient != null) {
			return iotClient;
		}

		iotClient = new AWSIotClient(new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY));
		iotClient.setRegion(Region.getRegion(Regions.fromName(Config.AWS_REGION)));

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		return iotClient;
	}

	protected AWSIotDataClient getDataClient() {
		if (iotDataClient != null) {
			return iotDataClient;
		}

		iotDataClient = new AWSIotDataClient(new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY));
		iotDataClient.setRegion(Region.getRegion(Regions.fromName(Config.AWS_REGION)));

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownDataClient();
			}
		});

		return iotDataClient;
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected void shutdownClient() {
		if (iotClient != null) {
			iotClient.shutdown();
			iotClient = null;
		}
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected void shutdownDataClient() {
		if (iotDataClient != null) {
			iotDataClient.shutdown();
			iotDataClient = null;
		}
	}

	@Override
	public Thing createThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getName()) || StringUtils.isBlank(thing.getAppid()) ||
				existsThing(thing)) {
			return null;
		}
		thing.setId(Utils.getNewId());
		String id = cloudIDForThing(thing);
		String appid = thing.getAppid();

		// STEP 1: Create thing
		CreateThingResult resp1 = getClient().createThing(new CreateThingRequest().withThingName(id).
				withAttributePayload(new AttributePayload().addAttributesEntry(Config._APPID, appid)));

		// STEP 2: Create certificate
		CreateKeysAndCertificateResult resp2 = getClient().createKeysAndCertificate(
				new CreateKeysAndCertificateRequest().withSetAsActive(true));

		String accountId = getAccountIdFromARN(resp1.getThingArn());
		String policyString = (String) (thing.getDeviceMetadata().containsKey("policyJSON") ?
				thing.getDeviceMetadata().get("policyJSON") : getDefaultPolicyDocument(accountId, id));

		// STEP 3: Create policy
		getClient().createPolicy(new CreatePolicyRequest().
				withPolicyDocument(policyString).withPolicyName(id + "-Policy"));

		// STEP 4: Attach policy to certificate
		getClient().attachPrincipalPolicy(new AttachPrincipalPolicyRequest().
				withPrincipal(resp2.getCertificateArn()).withPolicyName(id + "-Policy"));

		// STEP 5: Attach thing to certificate
		getClient().attachThingPrincipal(new AttachThingPrincipalRequest().
				withPrincipal(resp2.getCertificateArn()).withThingName(id));

		thing.getDeviceMetadata().remove("policyJSON");

		thing.setServiceBroker("AWS");
		thing.getDeviceMetadata().put("thingId", thing.getId());
		thing.getDeviceMetadata().put("thingName", id);
		thing.getDeviceMetadata().put("thingARN", resp1.getThingArn());
		thing.getDeviceMetadata().put("clientId", id);
		thing.getDeviceMetadata().put("clientCertId", resp2.getCertificateId());
		thing.getDeviceMetadata().put("clientCertARN", resp2.getCertificateArn());
		thing.getDeviceMetadata().put("clientCert", resp2.getCertificatePem());
		thing.getDeviceMetadata().put("privateKey", resp2.getKeyPair().getPrivateKey());
		thing.getDeviceMetadata().put("publicKey", resp2.getKeyPair().getPublicKey());
		thing.getDeviceMetadata().put("region", Config.AWS_REGION);
		thing.getDeviceMetadata().put("port", 8883);
		thing.getDeviceMetadata().put("host", getClient().
				describeEndpoint(new DescribeEndpointRequest()).getEndpointAddress());

		return thing;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> readThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId())) {
			return Collections.emptyMap();
		}
		String id = cloudIDForThing(thing);
		ByteBuffer bb =  getDataClient().getThingShadow(new GetThingShadowRequest().withThingName(id)).getPayload();
		if (bb != null) {
			ByteArrayInputStream bais = new ByteArrayInputStream(bb.array());
			try {
				Map<String, Object> payload = ParaObjectUtils.getJsonReader(Map.class).readValue(bais);
				if (payload != null && payload.containsKey("state")) {
					return (Map<String, Object>) ((Map<String, Object>) payload.get("state")).get("desired");
				}
			} catch (Exception ex) {
				logger.warn("Failed to connect to IoT device {}: {}", id, ex.getMessage());
			} finally {
				IOUtils.closeQuietly(bais);
			}
		}
		return Collections.emptyMap();
	}

	@Override
	public void updateThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId())) {
			return;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		String id = cloudIDForThing(thing);
		try {
			Object data = Collections.singletonMap("state", Collections.singletonMap("desired", thing.getDeviceState()));
			ParaObjectUtils.getJsonWriterNoIdent().writeValue(baos, data);
			getDataClient().updateThingShadow(new UpdateThingShadowRequest().
					withThingName(id).withPayload(ByteBuffer.wrap(baos.toByteArray())));
		} catch (Exception ex) {
			logger.warn("Failed to connect to IoT device {}: {}", id, ex.getMessage());
		} finally {
			IOUtils.closeQuietly(baos);
		}
	}

	@Override
	public void deleteThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId())) {
			return;
		}
		String id = cloudIDForThing(thing);
		String cARN = (String) thing.getDeviceMetadata().get("clientCertARN");
		String certId = (String) thing.getDeviceMetadata().get("clientCertId");
		String policy = id + "-Policy";
		for (PolicyVersion p : getClient().listPolicyVersions(new ListPolicyVersionsRequest().
				withPolicyName(policy)).getPolicyVersions()) {
			if (!p.isDefaultVersion()) {
				getClient().deletePolicyVersion(new DeletePolicyVersionRequest().
						withPolicyName(policy).withPolicyVersionId(p.getVersionId()));
			}
		}
		try {
			getClient().detachThingPrincipal(new DetachThingPrincipalRequest().
					withPrincipal(cARN).withThingName(id));
		} catch (Exception e) { }
		try {
			getClient().detachPrincipalPolicy(new DetachPrincipalPolicyRequest().
					withPrincipal(cARN).withPolicyName(policy));
		} catch (Exception e) { }
		try {
			getClient().deletePolicy(new DeletePolicyRequest().withPolicyName(policy));
		} catch (Exception e) { }
		try {
			getClient().updateCertificate(new UpdateCertificateRequest().withCertificateId(certId).
					withNewStatus(CertificateStatus.INACTIVE));
		} catch (Exception e) { }
		try {
			getClient().deleteCertificate(new DeleteCertificateRequest().withCertificateId(certId));
		} catch (Exception e) { }
		getClient().deleteThing(new DeleteThingRequest().withThingName(id));
		try {
			getDataClient().deleteThingShadow(new DeleteThingShadowRequest().withThingName(id));
		} catch (Exception e) { }
	}

	private String getDefaultPolicyDocument(String accountId, String id) {
		return "{"
			+ "  \"Version\": \"2012-10-17\","
			+ "  \"Statement\": ["
			+ "    {"
			+ "      \"Effect\": \"Allow\","
			+ "      \"Action\": [\"iot:Connect\"],"
			+ "      \"Resource\": [\"*\"]"
			+ "    },"
			+ "    {"
			+ "      \"Effect\": \"Allow\","
			+ "      \"Action\": [\"iot:Publish\"],"
			+ "      \"Resource\": ["
			+ "        \"arn:aws:iot:" + Config.AWS_REGION + ":" + accountId + ":topic/$aws/things/" + id + "/*\""
			+ "      ]"
			+ "    },"
			+ "    {"
			+ "      \"Effect\": \"Allow\","
			+ "      \"Action\": [\"iot:Receive\", \"iot:Subscribe\"],"
			+ "      \"Resource\": [\"*\"]"
			+ "    },"
			+ "    {"
			+ "      \"Effect\": \"Allow\","
			+ "      \"Action\": ["
			+ "        \"iot:UpdateThingShadow\","
			+ "        \"iot:GetThingShadow\""
			+ "      ],"
			+ "      \"Resource\": [\"arn:aws:iot:" + Config.AWS_REGION + ":" + accountId + ":thing/" + id + "\"]"
			+ "    }"
			+ "  ]"
			+ "}";
	}

	@Override
	public boolean existsThing(Thing thing) {
		if (thing == null) {
			return false;
		}
		try {
			return getClient().describeThing(new DescribeThingRequest().withThingName(cloudIDForThing(thing))) != null;
		} catch (Exception e) {
			return false;
		}
	}

	private String getAccountIdFromARN(String arn) {
		return StringUtils.contains(arn, ":") ? arn.split(":")[4] : "";
	}

	private String cloudIDForThing(Thing thing) {
		return thing.getAppid().concat("-").concat(thing.getId());
	}

}
