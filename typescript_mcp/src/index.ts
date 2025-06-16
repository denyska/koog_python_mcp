import express from "express";

import { McpServer, ResourceTemplate } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { z } from "zod";

const app = express();
app.use(express.json());

const transports = {
  streamable: {} as Record<string, StreamableHTTPServerTransport>,
  sse: {} as Record<string, SSEServerTransport>
};

app.post("/mcp", async (req, res) => {
  const server = initializeServer();
  try {
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
    });

    await server.connect(transport);

    await transport.handleRequest(req, res, req.body);

    req.on("close", () => {
      console.log("Request closed");
      transport.close();
      server.close();
    });
  } catch (e) {
    console.error("Error handling MCP request:", e);
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: "2.0",
        error: {
          code: -32603,
          message: "Internal server error",
        },
        id: null,
      });
    }
  }
});

// Legacy SSE endpoint for older clients
app.get('/sse', async (req, res) => {
  const server = initializeServer();

  // Create SSE transport for legacy clients
  const transport = new SSEServerTransport('/messages', res);
  transports.sse[transport.sessionId] = transport;
  
  res.on("close", () => {
    delete transports.sse[transport.sessionId];
  });
  
  await server.connect(transport);
});

// Legacy message endpoint for older clients
app.post('/messages', async (req, res) => {
  const sessionId = req.query.sessionId as string;
  const transport = transports.sse[sessionId];
  if (transport) {
    await transport.handlePostMessage(req, res, req.body);
  } else {
    res.status(400).send('No transport found for sessionId');
  }
});

export const initializeServer = (): McpServer => {
  const server = new McpServer({
    name: 'tbd-server',
    version: '1.0.0',
  }, { capabilities: { logging: {} } });

  server.tool("hello",
    { name: z.string().nullable().optional() },
    async ({ name }) => ({
      content: [{ type: "text", text: `Hello from TypeScript, ${name ? name : "stranger"}` }]
    })
  );

  return server
}


app.listen(8000, () => {
  console.log(`MCP server running on 8000}`)
})