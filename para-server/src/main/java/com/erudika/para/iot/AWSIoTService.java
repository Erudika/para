/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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

import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest;
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.AttributePayload;
import software.amazon.awssdk.services.iot.model.CertificateStatus;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.CreateThingResponse;
import software.amazon.awssdk.services.iot.model.DeleteCertificateRequest;
import software.amazon.awssdk.services.iot.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iot.model.DeletePolicyVersionRequest;
import software.amazon.awssdk.services.iot.model.DeleteThingRequest;
import software.amazon.awssdk.services.iot.model.DescribeEndpointRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest;
import software.amazon.awssdk.services.iot.model.DetachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.ListPolicyVersionsRequest;
import software.amazon.awssdk.services.iot.model.PolicyVersion;
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest;
import software.amazon.awssdk.services.iotdataplane.IotDataPlaneClient;
import software.amazon.awssdk.services.iotdataplane.model.DeleteThingShadowRequest;
import software.amazon.awssdk.services.iotdataplane.model.GetThingShadowRequest;
import software.amazon.awssdk.services.iotdataplane.model.UpdateThingShadowRequest;

/**
 * AWS IoT client.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class AWSIoTService implements IoTService {

	private static IotClient iotClient;
	private static IotDataPlaneClient iotDataClient;
	private static final Logger logger = LoggerFactory.getLogger(AWSIoTService.class);

	/**
	 * No-args constructor.
	 */
	public AWSIoTService() { }

	protected IotClient getClient() {
		if (iotClient != null) {
			return iotClient;
		}

		iotClient = IotClient.create();

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		return iotClient;
	}

	protected IotDataPlaneClient getDataClient() {
		if (iotDataClient != null) {
			return iotDataClient;
		}

		iotDataClient = IotDataPlaneClient.create();

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
			iotClient.close();
			iotClient = null;
		}
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected void shutdownDataClient() {
		if (iotDataClient != null) {
			iotDataClient.close();
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
		String region = new DefaultAwsRegionProviderChain().getRegion().id();

		// STEP 1: Create thing
		CreateThingResponse resp1 = getClient().createThing(CreateThingRequest.builder().thingName(id).
				attributePayload(AttributePayload.builder().attributes(Collections.singletonMap(Config._APPID, appid)).
						build()).build());

		// STEP 2: Create certificate
		CreateKeysAndCertificateResponse resp2 = getClient().createKeysAndCertificate(
				CreateKeysAndCertificateRequest.builder().setAsActive(true).build());

		String accountId = getAccountIdFromARN(resp1.thingArn());
		String policyString = (String) (thing.getDeviceMetadata().containsKey("policyJSON") ?
				thing.getDeviceMetadata().get("policyJSON") : getDefaultPolicyDocument(accountId, id, region));

		// STEP 3: Create policy
		getClient().createPolicy(CreatePolicyRequest.builder().
				policyDocument(policyString).policyName(id + "-Policy").build());

		// STEP 4: Attach policy to certificate
		getClient().attachPolicy(AttachPolicyRequest.builder().policyName(id + "-Policy").build());

		// STEP 5: Attach thing to certificate
		getClient().attachThingPrincipal(AttachThingPrincipalRequest.builder().
				principal(resp2.certificateArn()).thingName(id).build());

		thing.getDeviceMetadata().remove("policyJSON");

		thing.setServiceBroker("AWS");
		thing.getDeviceMetadata().put("thingId", thing.getId());
		thing.getDeviceMetadata().put("thingName", id);
		thing.getDeviceMetadata().put("thingARN", resp1.thingArn());
		thing.getDeviceMetadata().put("clientId", id);
		thing.getDeviceMetadata().put("clientCertId", resp2.certificateId());
		thing.getDeviceMetadata().put("clientCertARN", resp2.certificateArn());
		thing.getDeviceMetadata().put("clientCert", resp2.certificatePem());
		thing.getDeviceMetadata().put("privateKey", resp2.keyPair().privateKey());
		thing.getDeviceMetadata().put("publicKey", resp2.keyPair().publicKey());
		thing.getDeviceMetadata().put("region", region);
		thing.getDeviceMetadata().put("port", 8883);
		thing.getDeviceMetadata().put("host", getClient().
				describeEndpoint(DescribeEndpointRequest.builder().build()).endpointAddress());

		return thing;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> readThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId())) {
			return Collections.emptyMap();
		}
		String id = cloudIDForThing(thing);
		SdkBytes bb =  getDataClient().getThingShadow(GetThingShadowRequest.builder().thingName(id).build()).payload();
		if (bb != null) {
			try (ByteArrayInputStream bais = new ByteArrayInputStream(bb.asByteArray())) {
				Map<String, Object> payload = ParaObjectUtils.getJsonReader(Map.class).readValue(bais);
				if (payload != null && payload.containsKey("state")) {
					return (Map<String, Object>) ((Map<String, Object>) payload.get("state")).get("desired");
				}
			} catch (Exception ex) {
				logger.warn("Failed to connect to IoT device {}: {}", id, ex.getMessage());
			}
		}
		return Collections.emptyMap();
	}

	@Override
	public void updateThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getId())) {
			return;
		}
		String id = cloudIDForThing(thing);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			Object data = Collections.singletonMap("state", Collections.singletonMap("desired", thing.getDeviceState()));
			ParaObjectUtils.getJsonWriterNoIdent().writeValue(baos, data);
			getDataClient().updateThingShadow(UpdateThingShadowRequest.builder().
					thingName(id).payload(SdkBytes.fromByteArray(baos.toByteArray())).build());
		} catch (Exception ex) {
			logger.warn("Failed to connect to IoT device {}: {}", id, ex.getMessage());
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
		for (PolicyVersion p : getClient().listPolicyVersions(ListPolicyVersionsRequest.builder().
				policyName(policy).build()).policyVersions()) {
			if (!p.isDefaultVersion()) {
				getClient().deletePolicyVersion(DeletePolicyVersionRequest.builder().
						policyName(policy).policyVersionId(p.versionId()).build());
			}
		}
		try {
			getClient().detachThingPrincipal(DetachThingPrincipalRequest.builder().
					principal(cARN).thingName(id).build());
		} catch (Exception e) { }
		try {
			getClient().detachPolicy(DetachPolicyRequest.builder().policyName(policy).build());
		} catch (Exception e) { }
		try {
			getClient().deletePolicy(DeletePolicyRequest.builder().policyName(policy).build());
		} catch (Exception e) { }
		try {
			getClient().updateCertificate(UpdateCertificateRequest.builder().certificateId(certId).
					newStatus(CertificateStatus.INACTIVE).build());
		} catch (Exception e) { }
		try {
			getClient().deleteCertificate(DeleteCertificateRequest.builder().certificateId(certId).build());
		} catch (Exception e) { }
		getClient().deleteThing(DeleteThingRequest.builder().thingName(id).build());
		try {
			getDataClient().deleteThingShadow(DeleteThingShadowRequest.builder().thingName(id).build());
		} catch (Exception e) { }
	}

	private String getDefaultPolicyDocument(String accountId, String id, String region) {
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
			+ "        \"arn:aws:iot:" + region + ":" + accountId + ":topic/$aws/things/" + id + "/*\""
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
			+ "      \"Resource\": [\"arn:aws:iot:" + region + ":" + accountId + ":thing/" + id + "\"]"
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
			return getClient().describeThing(DescribeThingRequest.builder().thingName(cloudIDForThing(thing)).build()) != null;
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
