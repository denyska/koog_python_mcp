package com.tbd.koog_client

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(InternalAgentToolsApi::class)
fun main(): Unit = runBlocking {
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8000")
    )
    println("Found tools with DefaultMcpToolDescriptorParser : ${toolRegistry.tools}")


    val newToolRegistry = McpToolRegistryProvider.fromTransport(
        transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8000"),
        mcpToolParser = RecursiveMcpToolDescriptorParser
    )
    println("Found tools with RecursiveMcpToolDescriptorParser: ${newToolRegistry.tools}")

    val helloTool = newToolRegistry.tools[0] as McpTool

    val enabler = object : DirectToolCallsEnabler {}

    val responseNoParam = helloTool.executeAndSerialize(
         McpTool.Args(buildJsonObject {}),
        enabler
    ).first.promptMessageContents.first() as TextContent
    println("Response with no param: ${responseNoParam.text}")

    val responseWithParam = helloTool.executeAndSerialize(
        McpTool.Args(buildJsonObject { put("name", "world") }),
        enabler
    ).first.promptMessageContents.first() as TextContent
    println("Response with param: ${responseWithParam.text}")
}