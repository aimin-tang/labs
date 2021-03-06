#################################################################################
#
# MAKEFILE PLUGIN TO BE USED FOR FILTERING OUT YANG ANNOTATIONS UNSUPPORTED BY
# CERTAIN COMPILERS
#
# NOTE: Original of this file resides in nedcom, don't edit local copy in ned.
#
#################################################################################

# Python based cleaner to filter out yang annotations not supported be certain yang compilers
yang_cleaner = python -c 'import re, sys; s=sys.stdin.read(); s=re.sub(zzzREGEX, s); print(s.strip())'

# Regex to filter out yang annotations of type tailf:ned-data v3
NED_DATA_YANG_REGEX ="(tailf:ned-data\s*\"\S+\"\s+\{\s*[\r\n]\s*)(\S+\s+\S+;[\n\r]\s*)(\})","//\\1//\\2//\\3"
# Regex to filter out yang annotation of type tailf:ned-ignore-compare-config
NED_IGNORE_C_C_YANG_REGEX ="(tailf:ned-ignore-compare-config;)","//\\1"
# Regex to filter out yang annotation of type tailf:ned-default-handling (and tailf:cli-trim-default)
NED_IGNORE_D_H_YANG_REGEX ="(tailf:ned-default-handling|tailf:cli-trim-default)","//\\1"
# Regex to filter out augment to devices device platform
NED_IGNORE_D_P_YANG_REGEX = "\s*augment \"/ncs:devices/ncs:device/ncs:platform.*",""
# Regex to filter out new variations of yang annotations of type tailf:cli-diff-*
NED_NEW_DIFF_DEPS_REGEX="tailf:cli-diff-(delete|modify|create|set)-\S+\s+\"\S+\"\s*((;)|(\{[^\}]+\}))",""

# Regex to comment/uncomment the tag requires-transaction-states from package-meta-data.xml
PKG_META_DATA_REGEX="(?<!--)(<option>\s*[\r\n]\s*<name>requires-transaction-states</name>\s*[\r\n]\s*</option>)","<!--\\1-->"
PKG_META_DATA_REGEX_R="<!--(<option>\s*[\r\n]\s*<name>requires-transaction-states</name>\s*[\r\n]\s*</option>)-->","\\1"

NCS_VER = $(shell ($(NCS) --version))
NCS_VER_NUMERIC = $(shell ($(NCS) --version | sed s/_.*// | gawk -F. '{ printf("%02d%02d%02d%02d\n", $$1,$$2,$$3,$$4); }'))

ned-data-snippet.yang:
	@rm -f $@
	@echo "module ned-data-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-data';" >> $@
	@echo " prefix ned-data;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " leaf foo {" >> $@
	@echo "   tailf:ned-data "." {" >> $@
	@echo "     tailf:transaction both;" >> $@
	@echo "   }"  >> $@
	@echo "   type uint32;"  >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

ned-ignore-compare-config-snippet.yang:
	@rm -f $@
	@echo "module ned-ignore-compare-config-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-ignore-compare-config';" >> $@
	@echo " prefix ned-ignore-compare-config;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " leaf foo {" >> $@
	@echo "   tailf:ned-ignore-compare-config;" >> $@
	@echo "   type uint32;"  >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

ned-default-handling-snippet.yang:
	@rm -f $@
	@echo "module ned-default-handling-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-default-handling-snippet';" >> $@
	@echo " prefix ned-default-handling-snippet;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " leaf foo {" >> $@
	@echo "   tailf:ned-default-handling trim;" >> $@
	@echo "   type uint32;"  >> $@
	@echo "   default 0;"  >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

ned-device-platform-snippet.yang:
	@rm -f $@
	@echo "module ned-device-platform-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-device-platform-snippet';" >> $@
	@echo " prefix ned-device-platform-snippet;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " import tailf-ncs {" >> $@
	@echo "   prefix ncs;" >> $@
	@echo " }"  >> $@
	@echo " augment "/ncs:devices/ncs:device/ncs:platform" {" >> $@
	@echo "  leaf dummy { type string; }" >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

nso-ned-capabilities.properties:
	@rm -f $@
	@echo "# Property file auto generated for NSO $(NCS_VER)" > $@; \
	echo "nso-version=$(NCS_VER)" >> $@; \
	echo "nso-version-numeric=$(NCS_VER_NUMERIC)" >> $@; \
        if [ $(NCS_VER_NUMERIC) -ge 4040100 ]; then \
		echo "NSO SUPPORTS TRANSFER CONFIG AS XML"; \
		echo "supports-transfer-config-as-xml=yes" >> $@; \
	else \
		echo "NSO DOES NOT SUPPORT TRANSFER CONFIG AS XML"; \
		echo "supports-transfer-config-as-xml=no" >> $@; \
	fi

new-diff-deps-snippet.yang:
	@rm -f $@
	@echo "module new-diff-deps-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-clidiff';" >> $@
	@echo " prefix ned-clidiff;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " leaf foo {" >> $@
	@echo "   type uint32;"  >> $@
	@echo " }"  >> $@
	@echo " leaf bar {" >> $@
	@echo "   tailf:cli-diff-delete-before \"../foo\";"  >> $@
	@echo "   type uint32;"  >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

filter-yang: ned-data-snippet.yang ned-ignore-compare-config-snippet.yang ned-default-handling-snippet.yang ned-device-platform-snippet.yang  new-diff-deps-snippet.yang nso-ned-capabilities.properties

tmp-yang/%.yang: yang/%.yang
	@mkdir -p tmp-yang
	@cp yang/*.yang tmp-yang/
	@$(NCSC) --yangpath yang -c ned-data-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT ned-data. Filtering before compile"; \
		echo "supports-ned-data=no" >> nso-ned-capabilities.properties; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_DATA_YANG_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
		echo "Enabling commit-queue lock from package-meta-data.xml"; \
		for f in ../package-meta-data.xml; do  \
			cat $$f | $(subst zzzREGEX,$(PKG_META_DATA_REGEX_R),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
	else \
		echo "YANG COMPILER SUPPORTS ned-data. Disabling commit-queue lock from package-meta-data.xml"; \
		echo "supports-ned-data=yes" >> nso-ned-capabilities.properties; \
		for f in ../package-meta-data.xml; do  \
			cat $$f | $(subst zzzREGEX,$(PKG_META_DATA_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done \
	fi; \
	$(NCSC) --yangpath yang -c ned-ignore-compare-config-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT tailf:ned-ignore-compare-config. Filtering before compile"; \
		echo "supports-ignore-compare-config=no" >> nso-ned-capabilities.properties; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_IGNORE_C_C_YANG_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
	else \
		echo "YANG COMPILER SUPPORTS tailf:ned-ignore-compare-config"; \
		echo "supports-ignore-compare-config=yes" >> nso-ned-capabilities.properties; \
	fi; \
	$(NCSC) --yangpath yang -c ned-default-handling-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT tailf:ned-default-handling. Filtering before compile"; \
		echo "supports-default-handling-mode=no" >> nso-ned-capabilities.properties; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_IGNORE_D_H_YANG_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
	else \
		echo "YANG COMPILER SUPPORTS tailf:ned-default-handling"; \
		echo "supports-default-handling-mode=yes" >> nso-ned-capabilities.properties; \
	fi; \
	$(NCSC) --yangpath yang -c ned-device-platform-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT devices device platform. Filtering before compile"; \
		echo "supports-device-platform=no" >> nso-ned-capabilities.properties; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_IGNORE_D_P_YANG_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
	else \
		echo "YANG COMPILER SUPPORTS devices device platform"; \
		echo "supports-device-platform=yes" >> nso-ned-capabilities.properties; \
	fi; \
	$(NCSC) --yangpath yang -c new-diff-deps-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT new cli-diff-deps. Filtering before compile"; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_NEW_DIFF_DEPS_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
		echo "supports-new-diff-deps=no" >> nso-ned-capabilities.properties; \
	else \
		echo "YANG COMPILER SUPPORTS new cli-diff-deps"; \
		echo "supports-new-diff-deps=yes" >> nso-ned-capabilities.properties; \
	fi

CURRENT_DIR ?= $(shell pwd)
tmp-yang/schema.json: schema/jsondump.py tmp-yang/$(MAIN_YANG_MODULE)
	@echo "GENERATE SCHEMA FOR TURBO" ; \
	if [ $(NCS_VER_NUMERIC) -lt 4040100 ]; then \
		cat tmp-yang/$(MAIN_YANG_MODULE) | $(subst zzzREGEX,$(NED_DATA_YANG_REGEX),$(yang_cleaner)) | \
		$(subst zzzREGEX,$(NED_NEW_DIFF_DEPS_REGEX),$(yang_cleaner)) > tmp-yang/$(MAIN_YANG_MODULE).flt ; \
	else \
		cp tmp-yang/$(MAIN_YANG_MODULE) tmp-yang/$(MAIN_YANG_MODULE).flt ; \
	fi
	pyang -o tmp-yang/schema.json --json-pretty --json-cli $(PYANG_JSON_XARGS) --plugindir $(CURRENT_DIR)/schema/ -f json tmp-yang/$(MAIN_YANG_MODULE).flt

nedcom_cleaner = rm -f *.yang ; \
		rm -rf tmp-yang ; \
		rm -f *.properties ; \
		rm -f *.fxs ; \
		rm -f schema/jsondump.pyc

nedcom_tidy:
	@if [ "$(KEEP_FXS)" = "" -a `whoami` = jenkins ] ; then \
		$(nedcom_cleaner) ; \
	fi
.PHONY: nedcom_tidy

nedcom_clean:
	$(nedcom_cleaner)
.PHONY: nedcom_clean
