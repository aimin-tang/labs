from ats.topology import loader
from unicon.plugins.nso.settings import NsoSettings

tb = loader.load("asav-1-testbed.yaml")
ncs = tb.devices.ncs
nso_settings = NsoSettings()
nso_settings.ERROR_PATTERN=[]
ncs.connect(via='cli')

ncs.cli_style("cisco")
ncs.execute("packages reload")
config = """
load merge asav-1-load-merge.xml
commit
"""
ncs.configure(config)
ncs.execute("devices fetch-ssh-host-keys")
ncs.disconnect()
