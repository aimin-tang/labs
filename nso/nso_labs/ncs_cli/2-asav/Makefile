all: stop start cli

start:
	ncs-setup --dest .
	ncs-project update -y
	ncs

cli:
	ncs_cli -C -u admin
	packages reload
	config
	load merge asav-1.xml 
	commit
	end
	devices fetch-ssh-host-keys

stop:
	ncs-netsim stop || exit 0
	ncs --stop || exit 0
	rm -rf ./netsim running.DB logs/* state/* ncs-cdb/*.cdb
