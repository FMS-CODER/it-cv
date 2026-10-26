"""本地启动脚本：python run.py 即可拉起 uvicorn。"""

import uvicorn

from app.config import get_settings


def main() -> None:
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.server_port,
        reload=False,
        log_level="info",
    )


if __name__ == "__main__":
    main()
