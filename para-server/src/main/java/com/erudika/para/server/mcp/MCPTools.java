package com.erudika.para.server.mcp;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.User;
import com.erudika.para.core.annotations.Locked;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Pager;
import com.erudika.para.core.utils.Para;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.core.utils.Utils;
import static com.erudika.para.server.mcp.MCPUtils.requireRootApp;
import static com.erudika.para.server.mcp.MCPUtils.requireWritePermission;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools that expose Para diagnostics and configuration helpers. All tools require an authenticated App obtained via
 * SecurityUtils.getPrincipalApp().
 */
@Component
public class MCPTools {

	private static final Logger logger = LoggerFactory.getLogger(MCPTools.class);

	private final MCPUtils utils;

	/**
	 * Default constructor.
	 *
	 * @param utils the MCP utils object
	 */
	public MCPTools(MCPUtils utils) {
		this.utils = utils;
	}

	/**
	 * Searches the Para configuration metadata and returns the closest matches.
	 *
	 * @param query case-insensitive substring to match
	 * @param limit maximum number of matches to return
	 * @return the tool result containing configuration metadata
	 */
	@McpTool(name = "config_search",
			title = "Search Para Configuration",
			description = "Searches the Para configuration metadata and returns the closest matches. "
			+ "Use this when you don't know the exact configuration key name.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult searchConfig(
			@McpToolParam(description = "Case-insensitive substring to match (empty returns the first entries).") String query,
			@McpToolParam(description = "Maximum number of matches to return (default 10, max 50).") Integer limit) {
		try {
			App app = utils.authApp();
			logger.debug("[MCP] Config search by app={} query={} limit={}", app.getAppIdentifier(), query, limit);

			List<Map<String, Object>> results = utils.searchConfig(query, limit);
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("results", results);
			response.put("count", results.size());
			response.put("query", StringUtils.trimToEmpty(query));

			return CallToolResult.builder().structuredContent(response).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR, "Failed to search config: " + e.getMessage(), e);
		}
	}

	/**
	 * Gets detailed metadata and current value for a single configuration property.
	 *
	 * @param key the configuration property key
	 * @return the tool result containing config property details
	 */
	@McpTool(name = "get_config_by_key",
			title = "Get Configuration Property by Key",
			description = "Returns detailed metadata and the current value for a specific Para configuration property. "
			+ "Use this to look up exact configuration values by key name.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult getConfigurationByKey(
			@McpToolParam(description = "Configuration property key (e.g., 'para.mcp_server_mode').", required = true) String key) {
		try {
			App app = utils.authApp();
			String normalizedKey = StringUtils.trimToEmpty(key);

			if (StringUtils.isBlank(normalizedKey)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Configuration key cannot be empty");
			}

			logger.debug("[MCP] Get config by key={} by app={}", normalizedKey, app.getAppIdentifier());

			Map<String, Object> configEntry = utils.describeConfig(normalizedKey)
					.orElseThrow(() -> new MCPException(MCPException.ErrorType.NOT_FOUND,
					utils.buildUnknownKeyMessage(normalizedKey)));

			return CallToolResult.builder().structuredContent(configEntry).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to get configuration: " + e.getMessage(), e);
		}
	}

	/**
	 * Lists all Para object types and their statistics for the current app.
	 *
	 * @return the tool result containing object types and counts
	 */
	@McpTool(name = "list_types",
			title = "List Para Object Types and Statistics",
			description = "Returns all Para object types available in the current app, "
			+ "including the count of objects for each type and total object counts. "
			+ "Use this to understand the data model before searching or creating objects.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult listTypes() {
		try {
			App app = utils.authApp();
			String appid = app.getAppIdentifier();

			logger.debug("[MCP] List types by app={}", appid);

			Map<String, Object> stats = new LinkedHashMap<>();

			// Get total count across all types
			long totalCount = Para.getSearch().getCount(appid, "");
			stats.put("totalObjects", totalCount);

			// Get count for each type
			Map<String, String> types = ParaObjectUtils.getAllTypes(app);
			Map<String, Long> typeCounts = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : types.entrySet()) {
				String type = entry.getValue();
				long count = Para.getSearch().getCount(appid, type);
				typeCounts.put(type, count);
			}
			stats.put("typeCount", types.size());
			stats.put("types", typeCounts);

			return CallToolResult.builder().structuredContent(stats).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to list types", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to list types: " + e.getMessage(), e);
		}
	}

	/**
	 * Searches for Para objects using a query string.
	 *
	 * @param query search query string (Lucene syntax)
	 * @param type object type to search
	 * @param limit maximum number of results to return
	 * @param page page number for pagination
	 * @return the tool result containing search results
	 */
	@McpTool(name = "search",
			title = "Search for Para Objects of any type",
			description = "Searches for Para objects using a query string. Supports full-text search across all fields. "
			+ "Query syntax follows the Lucene query syntax.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult search(
			@McpToolParam(description = "Search query string (Lucene syntax, use '*' for all).", required = true) String query,
			@McpToolParam(description = "Object type to search (optional, searches all types if not specified).") String type,
			@McpToolParam(description = "Maximum number of results to return (default 10, max 1000).") Integer limit,
			@McpToolParam(description = "Page number for pagination (default 1).") Integer page) {
		try {
			App app = utils.authApp();
			query = StringUtils.isBlank(query) ? "*" : query;
			if (!Para.getSearch().isValidQueryString(query)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Invalid query string syntax: " + query);
			}
			Pager pager = new Pager();
			if (limit != null) {
				pager.setLimit(Math.min(Math.max(limit, 1), 1000)); // Clamp between 1 and 1000
			}
			if (page != null) {
				pager.setPage(Math.max(page, 1));
			}

			logger.debug("[MCP] Search by app={} query='{}' type={} limit={} page={}",
					app.getAppIdentifier(), query, type, pager.getLimit(), pager.getPage());

			List<ParaObject> results = Para.getSearch().findQuery(app.getAppIdentifier(), type, query, pager);
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("items", results);
			response.put("totalHits", pager.getCount());
			response.put("page", pager.getPage());
			response.put("limit", pager.getLimit());

			return CallToolResult.builder().structuredContent(response).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR, "Search failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Searches for a user by email address.
	 *
	 * @param email user email address to search for
	 * @return the tool result containing the user object
	 */
	@McpTool(name = "get_user_by_email",
			title = "Get User by Email",
			description = "Searches for a user by email address. Returns the matching user object if found.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult getUserByEmail(
			@McpToolParam(description = "User email address to search for.", required = true) String email) {
		try {
			App app = utils.authApp();

			if (StringUtils.isBlank(email)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Email address is required.");
			}

			logger.debug("[MCP] Get user by email for app={} email={}", app.getAppIdentifier(), email);
			HashMap<String, Object> terms = new HashMap<>(2);
			terms.put(Config._EMAIL, email);
			terms.put(Config._APPID, app.getAppIdentifier());
			Pager p = new Pager(1);
			List<User> results = Para.getSearch().findTerms(app.getAppIdentifier(), Utils.type(User.class), terms, true, p);
			if (results.isEmpty()) {
				throw new MCPException(MCPException.ErrorType.NOT_FOUND, "User not found with email: " + email);
			}
			return CallToolResult.builder().structuredContent(results.get(0)).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to get user by email: " + e.getMessage(), e);
		}
	}

	/**
	 * Retrieves a Para object by its ID.
	 *
	 * @param objectId object ID to retrieve
	 * @return the tool result containing the object
	 */
	@McpTool(name = "get_object_by_id",
			title = "Read a Para Object by ID",
			description = "Retrieves a Para object by its ID. Returns the object if found.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult getObjectById(
			@McpToolParam(description = "Object ID to retrieve.", required = true) String objectId) {
		try {
			App app = utils.authApp();

			if (StringUtils.isBlank(objectId)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Object ID is required.");
			}
			logger.debug("[MCP] Get object by ID for app={} id={}", app.getAppIdentifier(), objectId);

			ParaObject obj = Para.getDAO().read(app.getAppIdentifier(), objectId);

			if (obj == null) {
				throw new MCPException(MCPException.ErrorType.NOT_FOUND, "Object not found with ID: " + objectId);
			}
			return CallToolResult.builder().structuredContent(obj).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to get object by ID: " + e.getMessage(), e);
		}
	}

	/**
	 * Gets all app-specific settings for the current app.
	 *
	 * @return the tool result containing app settings
	 */
	@McpTool(name = "get_app_settings",
			title = "Get App Settings",
			description = "Returns all app-specific settings for the current app. "
			+ "App settings are a subset of Para configuration that can be customized per app, "
			+ "such as OAuth2 authentication settings, which differ between apps.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = true,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult getAppSettings() {
		try {
			App app = utils.authApp();
			logger.debug("[MCP] Get app settings for app={}", app.getAppIdentifier());
			return CallToolResult.builder().structuredContent(app.getSettings()).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to get app settings", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to get app settings: " + e.getMessage(), e);
		}
	}

	/**
	 * Sets or updates an app-specific setting by key.
	 *
	 * @param key setting key to set
	 * @param value setting value (string, number, boolean, or JSON)
	 * @return the tool result containing updated settings
	 */
	@McpTool(name = "put_app_setting",
			title = "Set App Setting",
			description = "Sets or updates an app-specific setting by key. "
			+ "App settings are a subset of Para configuration that can be customized per app. "
			+ "Only available when MCP server is in read-write mode.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult putAppSetting(
			@McpToolParam(description = "Setting key (e.g., 'fb_app_id', 'security.signin_success_path').", required = true) String key,
			@McpToolParam(description = "Setting value (string, number, boolean, or null to remove).", required = true) Object value) {
		requireWritePermission();

		try {
			App app = utils.authApp();
			if (StringUtils.isBlank(key)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Setting key cannot be empty");
			}
			logger.info("[MCP] Put app setting for app={} key={}", app.getAppIdentifier(), key);

			Map<String, Object> settings = app.getSettings();
			// Set or remove the setting
			if (value == null) {
				settings.remove(key);
				logger.info("[MCP] Removed app setting key={}", key);
			} else {
				settings.put(key, value);
				logger.info("[MCP] Set app setting key={} value={}", key, value);
			}
			app.setSettings(settings);
			Para.getDAO().update(app.getAppIdentifier(), app);
			return CallToolResult.builder().textContent(List.of(value == null ? "Setting removed." : "Setting updated.")).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to put app setting", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to put app setting: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a new Para application.
	 *
	 * @param appIdentifier app identifier
	 * @param name app display name
	 * @return the tool result containing app credentials
	 */
	@McpTool(name = "create_app",
			title = "Create a new Para App",
			description = "Creates a new Para application. Only available to root app when MCP server is in read-write mode. "
			+ "Returns the newly created app with its credentials.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = true,
					idempotentHint = false,
					openWorldHint = false
			))
	public CallToolResult createApp(
			@McpToolParam(description = "App identifier (required, alphanumeric with dashes/underscores).",
					required = true) String appIdentifier,
			@McpToolParam(description = "App display name (optional).") String name) {

		requireWritePermission();
		try {
			App app = utils.authApp();
			requireRootApp(app);
			if (StringUtils.isBlank(appIdentifier)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT,
						"App identifier is required.");
			}
			logger.info("[MCP] Creating new app '{}' by root app", appIdentifier);
			Map<String, String> credentials = Para.newApp(appIdentifier, name, false, false);
			if (credentials == null || credentials.isEmpty()) {
				throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
						"Failed to create app. App with that ID may already exist.");
			}
			logger.info("[MCP] Successfully created app '{}' with ID '{}'", name, appIdentifier);
			return CallToolResult.builder().structuredContent(credentials).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to create app: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a new Para object with user-provided data.
	 *
	 * @param type object type (e.g., "user", "sysprop", "tag")
	 * @param name object display name
	 * @param fields optional field values as JSON object/map
	 * @return the tool result containing the created object
	 */
	@McpTool(name = "create_object",
			title = "Create a New Para Object",
			description = "Creates a new Para object with user-provided data. Provide the object type, name, and "
			+ "optionally a JSON object with field values. Only available when MCP server is in read-write mode. "
			+ "IMPORTANT: If the tool returns an error about missing required fields, you MUST ask the user to provide "
			+ "those specific field values. DO NOT generate, guess, or infer field values. Always ask the user explicitly.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = true,
					idempotentHint = false,
					openWorldHint = false
			))
	public CallToolResult createObject(
			@McpToolParam(description = "Object type (e.g., 'user', 'sysprop', 'tag').", required = true) String type,
			@McpToolParam(description = "Object display name.", required = true) String name,
			@McpToolParam(description = "Optional field values as JSON object (e.g., "
					+ "{\"email\": \"user@example.com\", \"password\": \"secret\"}).") Map<String, Object> fields) {
		requireWritePermission();

		try {
			App app = utils.authApp();

			if (StringUtils.isBlank(type) || StringUtils.isBlank(name)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Object type and name are required");
			}
			// Step 1: Create object instance
			ParaObject obj = ParaObjectUtils.toObject(app, type);
			if (obj == null) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Unknown object type: " + type);
			}
			obj.setName(name);

			// Step 2: Check for required fields
			Map<String, Class<?>> requiredFields = utils.getRequiredFields(obj);
			Map<String, Object> providedFields = fields != null ? new LinkedHashMap<>(fields) : new LinkedHashMap<>();

			// Validate that all required fields are provided
			if (!requiredFields.isEmpty()) {
				List<String> missingFields = new ArrayList<>();
				Map<String, String> fieldTypes = new LinkedHashMap<>();
				for (Map.Entry<String, Class<?>> entry : requiredFields.entrySet()) {
					String fieldName = entry.getKey();
					if (!providedFields.containsKey(fieldName) || providedFields.get(fieldName) == null) {
						missingFields.add(fieldName);
						fieldTypes.put(fieldName, entry.getValue().getSimpleName());
					}
				}

				if (!missingFields.isEmpty()) {
					StringBuilder errorBuilder = new StringBuilder();
					errorBuilder.append("❌ Cannot create object: Missing required fields.\n\n");
					errorBuilder.append("Required fields:\n");
					for (String field : missingFields) {
						errorBuilder.append("  • ").append(field).append(" (").append(fieldTypes.get(field)).append(")\n");
					}
					errorBuilder.append("\n⚠️  IMPORTANT: You MUST ask the user to provide values for these fields. ");
					errorBuilder.append("DO NOT generate, guess, or make up values. Ask the user explicitly for each field.");

					String error = errorBuilder.toString();
					return CallToolResult.builder().isError(true).addTextContent(error).build();
				}
			}

			// Step 3: Set field values
			ParaObjectUtils.setAnnotatedFields(obj, providedFields, null);

			// Step 4: Create the object
			String createdId = Para.getDAO().create(app.getAppIdentifier(), obj);

			if (StringUtils.isBlank(createdId)) {
				throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR, "Failed to create object");
			}

			// Read back the created object
			ParaObject created = Para.getDAO().read(app.getAppIdentifier(), createdId);
			logger.info("[MCP] Successfully created object id={} type={} name={}", createdId, type, name);

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("message", "Object created successfully");
			response.put("object", created);
			return CallToolResult.builder().structuredContent(response).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to create object", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to create object: " + e.getMessage(), e);
		}
	}

	/**
	 * Updates an existing Para object with provided field values.
	 *
	 * @param objectId object ID to update
	 * @param fields field values to update as JSON object/map
	 * @return the tool result containing the updated object
	 */
	@McpTool(name = "update_object",
			title = "Update a Para Object",
			description = "Updates an existing Para object with provided field values. Provide the object ID and "
			+ "a JSON object with field name-value pairs to update. Only available when MCP server is in read-write mode.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = false,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult updateObject(
			@McpToolParam(description = "Object ID to update.", required = true) String objectId,
			@McpToolParam(description = "Field values to update as JSON object (e.g., {\"name\": \"New Name\", "
					+ "\"email\": \"new@example.com\"}).", required = true) Map<String, Object> fields) {
		requireWritePermission();

		try {
			App app = utils.authApp();

			if (StringUtils.isBlank(objectId)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Object ID is required.");
			}

			if (fields == null || fields.isEmpty()) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "No fields to update provided.");
			}

			// Step 1: Read the object
			ParaObject obj = Para.getDAO().read(app.getAppIdentifier(), objectId);

			if (obj == null) {
				throw new MCPException(MCPException.ErrorType.NOT_FOUND, "Object not found with ID: " + objectId);
			}

			// Step 2: Apply updates
			ParaObject updated = ParaObjectUtils.setAnnotatedFields(obj, fields, Locked.class);
			updated.update();
			logger.info("[MCP] Updated object id={} type={} with {} field(s)", objectId, obj.getType(), fields.size());

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("message", "Object updated successfully with " + fields.size() + " field(s)");
			response.put("updatedFields", fields.keySet());
			response.put("object", updated);

			return CallToolResult.builder().structuredContent(response).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to update object", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to update object: " + e.getMessage(), e);
		}
	}

	/**
	 * Deletes an existing Para object.
	 *
	 * @param objectId object ID to delete
	 * @return the tool result
	 */
	@McpTool(name = "delete_object",
			title = "Delete a Para Object",
			description = "Deletes an existing Para object by ID. Only available when MCP server is in read-write mode. "
			+ "WARNING: This operation cannot be undone. The object will be permanently deleted.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = true,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult deleteObject(
			@McpToolParam(description = "Object ID to delete.", required = true) String objectId) {
		requireWritePermission();

		try {
			App app = utils.authApp();

			if (StringUtils.isBlank(objectId)) {
				throw new MCPException(MCPException.ErrorType.INVALID_INPUT, "Object ID is required.");
			}

			// Step 1: Read the object
			ParaObject obj = Para.getDAO().read(app.getAppIdentifier(), objectId);

			if (obj == null) {
				throw new MCPException(MCPException.ErrorType.NOT_FOUND, "Object not found with ID: " + objectId);
			}

			// Step 2: Delete the object
			Para.getDAO().delete(app.getAppIdentifier(), obj);
			logger.info("[MCP] Deleted object id={} type={} name={} by app={}",
					objectId, obj.getType(), obj.getName(), app.getAppIdentifier());

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("message", "Object deleted successfully");
			response.put("deletedId", objectId);
			response.put("deletedType", obj.getType());
			response.put("deletedName", obj.getName());

			return CallToolResult.builder().structuredContent(response).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to delete object", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to delete object: " + e.getMessage(), e);
		}
	}

	/**
	 * Rebuilds the search index for all objects in the app.
	 *
	 * @return the tool result
	 */
	@McpTool(name = "rebuild_index",
			title = "Rebuild Search Index",
			description = "Rebuilds the search index for all objects in the current app. This operation reindexes "
			+ "all objects from the database. Only available when MCP server is in read-write mode. "
			+ "Use this when search results are inconsistent or after bulk data changes.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = true,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult rebuildIndex() {
		requireWritePermission();

		try {
			App app = utils.authApp();
			String appid = app.getAppIdentifier();
			logger.info("[MCP] Rebuilding search index for app={}", appid);
			Para.getSearch().rebuildIndex(Para.getDAO(), app);
			return CallToolResult.builder().textContent(List.of("Search index rebuilt.")).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to rebuild search index", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to rebuild search index: " + e.getMessage(), e);
		}
	}

	/**
	 * Clears all cached data for the app.
	 *
	 * @return the tool result
	 */
	@McpTool(name = "clear_cache",
			title = "Clear Object Cache",
			description = "Clears all cached data for the current app. This removes all entries from the cache, "
			+ "forcing fresh reads from the database. Only available when MCP server is in read-write mode. "
			+ "Use this to resolve stale data issues or after configuration changes.",
			annotations = @McpTool.McpAnnotations(
					readOnlyHint = false,
					destructiveHint = true,
					idempotentHint = true,
					openWorldHint = false
			))
	public CallToolResult clearCache() {
		requireWritePermission();

		try {
			App app = utils.authApp();
			String appid = app.getAppIdentifier();
			Para.getCache().removeAll(appid);
			return CallToolResult.builder().textContent(List.of("Cache cleared.")).build();
		} catch (MCPException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to clear cache", e);
			throw new MCPException(MCPException.ErrorType.INTERNAL_ERROR,
					"Failed to clear cache: " + e.getMessage(), e);
		}
	}

}
