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
import com.amazonaws.services.iot.AWSIotAsyncClient;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.client.AWSIotDevice;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.model.Action;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.AttachThingPrincipalRequest;
import com.amazonaws.services.iot.model.AttributePayload;
import com.amazonaws.services.iot.model.CertificateStatus;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.amazonaws.services.iot.model.CreatePolicyRequest;
import com.amazonaws.services.iot.model.CreateThingRequest;
import com.amazonaws.services.iot.model.CreateThingResult;
import com.amazonaws.services.iot.model.CreateTopicRuleRequest;
import com.amazonaws.services.iot.model.DeleteCertificateRequest;
import com.amazonaws.services.iot.model.DeletePolicyRequest;
import com.amazonaws.services.iot.model.DeletePolicyVersionRequest;
import com.amazonaws.services.iot.model.DeleteThingRequest;
import com.amazonaws.services.iot.model.DeleteTopicRuleRequest;
import com.amazonaws.services.iot.model.DescribeEndpointRequest;
import com.amazonaws.services.iot.model.DescribeThingRequest;
import com.amazonaws.services.iot.model.DetachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.DetachThingPrincipalRequest;
import com.amazonaws.services.iot.model.ListPolicyVersionsRequest;
import com.amazonaws.services.iot.model.PolicyVersion;
import com.amazonaws.services.iot.model.SqsAction;
import com.amazonaws.services.iot.model.TopicRulePayload;
import com.amazonaws.services.iot.model.UpdateCertificateRequest;
import com.erudika.para.DestroyListener;
import com.erudika.para.Para;
import com.erudika.para.core.Thing;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.persistence.AWSDynamoUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWS IoT client.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSIoT {

	private static AWSIotClient iotClient;
	private static AWSIotAsyncClient iotAsyncClient;
	private static final String QUEUE = Config.DEFAULT_QUEUE_NAME;
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoUtils.class);

	private AWSIoT() { }

	public static AWSIotClient getClient() {
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

	public static AWSIotAsyncClient getAsyncClient() {
		if (iotAsyncClient != null) {
			return iotAsyncClient;
		}

		iotAsyncClient = new AWSIotAsyncClient(new BasicAWSCredentials(Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY));
		iotAsyncClient.setRegion(Region.getRegion(Regions.fromName(Config.AWS_REGION)));

		Para.addDestroyListener(new DestroyListener() {
			public void onDestroy() {
				shutdownAsyncClient();
			}
		});

		return iotAsyncClient;
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownClient() {
		if (iotClient != null) {
			iotClient.shutdown();
			iotClient = null;
		}
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownAsyncClient() {
		if (iotAsyncClient != null) {
			iotAsyncClient.shutdown();
			iotAsyncClient = null;
		}
	}

	public static Thing createThing(String appid, String name) {
		if (StringUtils.isBlank(name) || StringUtils.isBlank(appid) || existsThing(name)) {
			return null;
		}
		// STEP 1: Create thing
		CreateThingResult resp1 = getClient().createThing(new CreateThingRequest().withThingName(name).
				withAttributePayload(new AttributePayload().addAttributesEntry(Config._APPID, appid)));

		// STEP 2: Create certificate
		CreateKeysAndCertificateResult resp2 = getClient().createKeysAndCertificate(
				new CreateKeysAndCertificateRequest().withSetAsActive(true));

		String accountId = getAccountIdFromARN(resp1.getThingArn());
		String queueURL = "https://sqs." + Config.AWS_REGION + ".amazonaws.com/" + accountId + "/" + QUEUE;
		String roleARN = "arn:aws:iam::" + accountId + ":role/" + Config.AWS_QUEUE_ROLE;
		String thingId = Utils.getNewId();
		String type = Utils.type(Thing.class);
		String roleSQL = "SELECT state, version, "
				+ "'" + thingId + "' as " + Config._ID + ", "
				+ "'" + appid + "' as " + Config._APPID + ", "
				+ "'" + type + "' as " + Config._TYPE + " FROM '$aws/things/" + name + "/shadow/update/delta'";

		// STEP 3: Create policy
		getClient().createPolicy(new CreatePolicyRequest().
				withPolicyDocument(getDefaultPolicyDocument(accountId, name)).
				withPolicyName(name + "-Policy"));

		// STEP 4: Attach policy to certificate
		getClient().attachPrincipalPolicy(new AttachPrincipalPolicyRequest().
				withPrincipal(resp2.getCertificateArn()).withPolicyName(name + "-Policy"));

		// STEP 5: Attach thing to certificate
		getClient().attachThingPrincipal(new AttachThingPrincipalRequest().
				withPrincipal(resp2.getCertificateArn()).withThingName(name));

		// STEP 6: Create rule to send thing state to SQS queue
		getClient().createTopicRule(new CreateTopicRuleRequest().withRuleName(name + "_SQS_Link").
				withTopicRulePayload(new TopicRulePayload().
						withActions(new Action().withSqs(new SqsAction().
								withQueueUrl(queueURL).
								withRoleArn(roleARN).
								withUseBase64(false))).
						withSql(roleSQL).
						withRuleDisabled(false)));

		Thing thing = new Thing(thingId);
		thing.setAppid(appid);
		thing.setName(name);
		thing.setServiceBroker("AWS");

		Map<String, Object> metadata = new LinkedHashMap<String, Object>(15);
		metadata.put("thingId", thing.getId());
		metadata.put("thingName", thing.getName());
		metadata.put("thingARN", resp1.getThingArn());
		metadata.put("clientId", thing.getName());
		metadata.put("clientCertId", resp2.getCertificateId());
		metadata.put("clientCertARN", resp2.getCertificateArn());
		metadata.put("clientCert", resp2.getCertificatePem());
		metadata.put("privateKey", resp2.getKeyPair().getPrivateKey());
		metadata.put("publicKey", resp2.getKeyPair().getPublicKey());
		metadata.put("region", Config.AWS_REGION);
		metadata.put("host", getClient().describeEndpoint(new DescribeEndpointRequest()).getEndpointAddress());
		metadata.put("port", 8883);

		thing.setMetadata(metadata);
		thing.create();

		return thing;
	}

	public static void updateThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getName())) {
			return;
		}

		String host = (String) thing.getMetadata().get("host");
		String clientId = thing.getName() + "Serverside";
		try {
			AWSIotMqttClient client = new AWSIotMqttClient(host, clientId, Config.AWS_ACCESSKEY, Config.AWS_SECRETKEY);
			AWSIotDevice device = new AWSIotDevice(thing.getName());
			client.setMaxConnectionRetries(0);
			client.setKeepAliveInterval(0);
			client.setNumOfClientThreads(1);
			client.setServerAckTimeout(0);

			device.setReportInterval(0);
			client.attach(device);
			client.connect();
			device.update("{\"state\": { \"desired\":" +
					ParaObjectUtils.getJsonWriterNoIdent().writeValueAsString(thing.getState()) + "}}");
			client.detach(device);
			client.disconnect(10000, false);
		} catch (Exception ex) {
			logger.warn("Failed to connect to IoT device {}: {}", thing.getName(), ex.getMessage());
		}
	}

	public static void deleteThing(Thing thing) {
		if (thing == null || StringUtils.isBlank(thing.getName())) {
			return;
		}
		String name = thing.getName();
		String cARN = (String) thing.getMetadata().get("clientCertARN");
		String certId = (String) thing.getMetadata().get("clientCertId");
		String policy = name + "-Policy";
		for (PolicyVersion p : getClient().listPolicyVersions(new ListPolicyVersionsRequest().
				withPolicyName(policy)).getPolicyVersions()) {
			if (!p.isDefaultVersion()) {
				getClient().deletePolicyVersion(new DeletePolicyVersionRequest().
						withPolicyName(policy).withPolicyVersionId(p.getVersionId()));
			}
		}
		getClient().detachThingPrincipal(new DetachThingPrincipalRequest().withPrincipal(cARN).withThingName(name));
		getClient().detachPrincipalPolicy(new DetachPrincipalPolicyRequest().withPrincipal(cARN).withPolicyName(policy));
		getClient().deletePolicy(new DeletePolicyRequest().withPolicyName(policy));
		getClient().updateCertificate(new UpdateCertificateRequest().withCertificateId(certId).
				withNewStatus(CertificateStatus.INACTIVE));
		getClient().deleteCertificate(new DeleteCertificateRequest().withCertificateId(certId));
		getClient().deleteTopicRule(new DeleteTopicRuleRequest().withRuleName(name + "_SQS_Link"));
		getClient().deleteThing(new DeleteThingRequest().withThingName(name));
		thing.delete();
	}

	private static String getDefaultPolicyDocument(String accountId, String name) {
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
			+ "        \"arn:aws:iot:" + Config.AWS_REGION + ":" + accountId + ":topic/$aws/things/" + name + "/*\""
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
			+ "      \"Resource\": [\"arn:aws:iot:" + Config.AWS_REGION + ":" + accountId + ":thing/" + name + "\"]"
			+ "    }"
			+ "  ]"
			+ "}";
	}

	private static boolean existsThing(String name) {
		try {
			return getClient().describeThing(new DescribeThingRequest().withThingName(name)) != null;
		} catch (Exception e) {
			return false;
		}
	}

	private static String getAccountIdFromARN(String arn) {
		return StringUtils.contains(arn, ":") ? arn.split(":")[4] : "";
	}

}
