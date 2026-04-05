package com.erudika.para.server.mcp;

import com.erudika.para.core.App;
import static com.erudika.para.server.mcp.MCPUtils.jsonResource;
import static com.erudika.para.server.mcp.MCPUtils.textResource;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Para MCP server resources that expose Para data over the Model Context Protocol. All resources require an
 * authenticated App obtained via SecurityUtils.getPrincipalApp().
 */
@Component
public class MCPResources {

	private static final Logger logger = LoggerFactory.getLogger(MCPResources.class);

	private final MCPUtils utils;

	/**
	 * Default constructor.
	 * @param utils MCP utils
	 */
	public MCPResources(MCPUtils utils) {
		this.utils = utils;
	}

	/**
	 * Returns the root index of available MCP resources and tools.
	 * @return the root resource result
	 */
	@McpResource(uri = "para:///",
			name = "root",
			title = "Para MCP Server Index",
			description = "Root resource listing available Para MCP resources and tools.",
			mimeType = "application/json")
	public ReadResourceResult readRoot() {
		App app = utils.authApp();
		logger.debug("[MCP] Root index accessed by app={}", app.getAppIdentifier());
		Map<String, Object> index = new LinkedHashMap<>();
		index.put("server", "Para MCP Server");
		index.put("version", "1.0");
		index.put("appIdentifier", app.getAppIdentifier());
		index.put("mcpMode", utils.getMcpMode());
		index.put("tools", utils.listAllTools());

		return jsonResource("para:///", index);
	}

	/**
	 * Returns the latest health status and metadata for this Para server.
	 * @return the health resource result
	 */
	@McpResource(uri = "para:///metadata",
			name = "metadata",
			title = "Para Server Information, Health and Metadata",
			description = "Returns the latest health status and metadata for this Para server.",
			mimeType = "application/json")
	public ReadResourceResult readMetadata() {
		App app = utils.authApp();
		logger.debug("[MCP] Metadata resource accessed by app={}", app.getAppIdentifier());
		return jsonResource("para:///metadata", utils.serverMetadata(app));
	}

	/**
	 * Returns the configuration documentation in Markdown format.
	 * @return the config documentation resource result
	 */
	@McpResource(uri = "para:///config",
			name = "config-docs",
			title = "Para Configuration Reference",
			description = "Browse the complete Para configuration reference with all available settings, "
			+ "descriptions, and default values. Use this to explore what's configurable before using specific tools.",
			mimeType = "text/markdown")
	public ReadResourceResult renderConfigDocumentation() {
		App app = utils.authApp();
		logger.debug("[MCP] Config documentation accessed by app={}", app.getAppIdentifier());
		return textResource("para:///config", "text/markdown", utils.renderConfigDocumentation());
	}

}
