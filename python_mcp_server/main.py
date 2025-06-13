import asyncio
import logging
from mcp.server.fastmcp import FastMCP

logger = logging.getLogger(__name__)

mcp = FastMCP("Demo")

@mcp.tool()
def hello(name: str | None = None) -> str:
    """Says hello"""
    name_to_use = name if name is not None else "stranger"
    return f"Hello, {name_to_use}!"


async def run_mcp_server():
    """Run the MCP server in the current event loop."""
    mcp.settings.host = "0.0.0.0"
    logger.info(
        f'Running MCP server with SSE transport on {mcp.settings.host}:{mcp.settings.port}'
    )
    await mcp.run_sse_async()

if __name__ == "__main__":
    """Main function to run the Graphiti MCP server."""
    try:
        asyncio.run(run_mcp_server())
    except Exception as e:
        logger.error(f'Error initializing Graphiti MCP server: {str(e)}')
        raise

