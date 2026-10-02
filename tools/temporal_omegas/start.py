"""Start or query the one canonical gate for the current Platina SHA."""
import argparse
import asyncio
import json

from temporalio.client import Client
from temporalio.envconfig import ClientConfig

from tools.temporal_omegas.worker import QUEUE
from tools.temporal_omegas.workflow import PlatinaSameShaGate

WORKFLOW_ID = "omegas-platina-same-sha-gate"


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["start", "status", "stop"])
    parser.add_argument("--max-checks", type=int, default=4)
    args = parser.parse_args()
    config = ClientConfig.load_client_connect_config()
    config.setdefault("target_host", "localhost:7233")
    client = await Client.connect(**config)
    if args.action == "start":
        # A duplicate running ID fails rather than spawning extra branches or loops.
        handle = await client.start_workflow(
            PlatinaSameShaGate.run, args.max_checks,
            id=WORKFLOW_ID, task_queue=QUEUE,
        )
        print(json.dumps({"workflow_id": handle.id, "run_id": handle.result_run_id}))
    else:
        handle = client.get_workflow_handle(WORKFLOW_ID)
        if args.action == "status":
            print(json.dumps(await handle.query(PlatinaSameShaGate.status), indent=2))
        else:
            await handle.signal(PlatinaSameShaGate.stop)
            print("Stop requested")


if __name__ == "__main__":
    asyncio.run(main())
