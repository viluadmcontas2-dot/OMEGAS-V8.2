"""Start a Temporal worker. Requires an existing Temporal server and GITHUB_TOKEN."""
import asyncio
from concurrent.futures import ThreadPoolExecutor

from temporalio.client import Client
from temporalio.envconfig import ClientConfig
from temporalio.worker import Worker

from tools.temporal_omegas.activities import resolve_platina_head, list_exact_sha_runs
from tools.temporal_omegas.workflow import PlatinaSameShaGate

QUEUE = "omegas-platina-readonly"


async def main():
    config = ClientConfig.load_client_connect_config()
    config.setdefault("target_host", "localhost:7233")
    client = await Client.connect(**config)
    with ThreadPoolExecutor(max_workers=4) as executor:
        worker = Worker(
            client, task_queue=QUEUE,
            workflows=[PlatinaSameShaGate],
            activities=[resolve_platina_head, list_exact_sha_runs],
            activity_executor=executor,
        )
        await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
