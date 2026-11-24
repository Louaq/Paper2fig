"""Top-Conf Figure Gallery offline pipeline.

Offline, resumable stages:

    discover -> fetch (PDF cache) -> crop -> qc -> (review in admin) -> publish

Every stage is a pure function over a workspace directory, so the admin
(Java/Vue, yudao) can drive it as a subprocess and keep the database as the
system of record. The CLI is also usable standalone.
"""

__version__ = "1.0.0"
ALGO_VERSION = "crop-layout-1.0"
