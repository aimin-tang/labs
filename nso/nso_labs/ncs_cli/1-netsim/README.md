# Summary

* NSO 4.5 installation is completed beforehand.
* build a total of 3 cisco routers with netsim.
* start netsim and ncs.
* start ncs_cli to configure/monitor routers.
* stop netsim, stop ncs, and clean up appropriately

# CLI

## start netsim and ncs, from an empty directory:

```
ncs-netsim create-network $NCS_DIR/packages/neds/cisco-ios 3 c
ncs-netsim start
ncs-setup --netsim-dir ./netsim --dest .
ncs
```

## start ncs CLI

```
ncs_cli -C -u admin
```

## stop
```
ncs-netsim stop
ncs --stop
<can delete all files if nothing to save>
```

## Makefile

```
make start
make stop
make cli
make all # same as make stop start cli
```
