package com.tbd.koog_client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.mcp.McpToolDescriptorParser
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contains
import kotlin.collections.first
import kotlin.collections.map
import kotlin.text.lowercase


public object RecursiveMcpToolDescriptorParser : McpToolDescriptorParser {
    // Maximum depth of recursive parsing
    private const val MAX_DEPTH = 10

    /**
     * Parses an MCP SDK Tool definition into tool descriptor format.
     *
     * This method extracts tool information (name, description, parameters) from an MCP SDK Tool
     * and converts it into a ToolDescriptor that can be used by the agent framework.
     *
     * @param sdkTool The MCP SDK Tool to parse.
     * @return A ToolDescriptor representing the MCP tool.
     */
    override fun parse(sdkTool: Tool): ToolDescriptor {
        // Parse all parameters from the input schema
        val parameters = parseParameters(sdkTool.inputSchema.properties)

        // Get the list of required parameters
        val requiredParameters = sdkTool.inputSchema.required ?: emptyList()

        // Create a ToolDescriptor
        return ToolDescriptor(
            name = sdkTool.name,
            description = sdkTool.description.orEmpty(),
            requiredParameters = parameters.filter { requiredParameters.contains(it.name) },
            optionalParameters = parameters.filter { !requiredParameters.contains(it.name) },
        )
    }

    private fun parseParameterType(element: JsonObject, depth: Int = 0): ToolParameterType {
        // Extract the type string from the JSON object
        val typeStr = element["type"]?.jsonPrimitive?.content

        if (typeStr == null) {
            /**
             * Special case for nullable types.
             * Schema example:
             * {
             *   "nullableParam": {
             *     "anyOf": [
             *       { "type": "string" },
             *       { "type": "null" }
             *     ],
             *     "title": "Nullable string parameter"
             *   }
             * }
             */
            val anyOf = element["anyOf"]?.jsonArray
            if (depth < MAX_DEPTH && anyOf != null && anyOf.size == 2) {
                val types = anyOf.map { it.jsonObject["type"]?.jsonPrimitive?.content }
                if (types.contains("null")) {
                    val nonNullType = anyOf.first {
                        it.jsonObject["type"]?.jsonPrimitive?.content != "null"
                    }.jsonObject
                    return parseParameterType(nonNullType, depth + 1)
                }
            }
            val title =
                element["title"]?.jsonPrimitive?.content ?: element["description"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalArgumentException("Parameter $title must have type property")
        }


        // Convert the type string to a ToolParameterType
        return when (typeStr.lowercase()) {
            // Primitive types
            "string" -> ToolParameterType.String
            "integer" -> ToolParameterType.Integer
            "number" -> ToolParameterType.Float
            "boolean" -> ToolParameterType.Boolean
            "enum" -> ToolParameterType.Enum(
                element.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toTypedArray()
            )

            // Array type
            "array" -> {
                val items = element["items"]?.jsonObject
                    ?: throw IllegalArgumentException("Array type parameters must have items property")

                val itemType = parseParameterType(items)

                ToolParameterType.List(itemsType = itemType)
            }

            // Object type
            "object" -> {
                val properties = element["properties"]?.let { properties ->
                    val rawProperties = properties.jsonObject
                    rawProperties.map { (name, property) ->
                        // Description is optional
                        val description = element["description"]?.jsonPrimitive?.content.orEmpty()
                        ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject))
                    }
                } ?: emptyList()

                val required = element["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()


                val additionalProperties = if ("additionalProperties" in element) {
                    when (element.getValue("additionalProperties")) {
                        is JsonPrimitive -> element.getValue("additionalProperties").jsonPrimitive.boolean
                        is JsonObject -> true
                        else -> null
                    }
                } else {
                    null
                }

                val additionalPropertiesType = if ("additionalProperties" in element) {
                    when (element.getValue("additionalProperties")) {
                        is JsonObject -> parseParameterType(element.getValue("additionalProperties").jsonObject)
                        else -> null
                    }
                } else {
                    null
                }

                ToolParameterType.Object(
                    properties = properties,
                    requiredProperties = required,
                    additionalPropertiesType = additionalPropertiesType,
                    additionalProperties = additionalProperties
                )
            }

            // Unsupported type
            else -> throw IllegalArgumentException("Unsupported parameter type: $typeStr")
        }
    }

    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            require(element is JsonObject) { "Parameter $name must be a JSON object" }

            // Extract description from the element
            val description = element["description"]?.jsonPrimitive?.content.orEmpty()

            // Parse the parameter type
            val type = parseParameterType(element)

            // Create a ToolParameterDescriptor
            ToolParameterDescriptor(
                name = name, description = description, type = type
            )
        }
    }


}