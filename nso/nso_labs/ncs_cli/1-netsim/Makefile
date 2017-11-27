all: stop start cli

start:
	ncs-netsim create-network $(NCS_DIR)/packages/neds/cisco-ios 3 c
	ncs-netsim start
	ncs-setup --netsim-dir ./netsim --dest .
	ncs

cli:
	ncs_cli -C -u admin

stop:
	ncs-netsim stop || exit 0
	ncs --stop || exit 0
	rm -rf ./netsim running.DB logs/* state/* ncs-cdb/*.cdb
