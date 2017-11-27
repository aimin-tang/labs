JAVA_PACKAGE = com.tailf.packages.ned.asa
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')
SHELL := /bin/bash
NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

all:	filter-yang fxs tmp-yang/schema.json javac netsim nedcom_tidy

netsim:
	pushd ../netsim && $(MAKE) all && popd

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

MAIN_YANG_MODULE = tailf-ned-cisco-asa.yang

-include ../../nedcom/src/nedcom.mk

include ned-yang-filter.mk

NCSC_VER := $(shell ${NCSC} --version)
ifneq (,$(findstring 6.2,${NCSC_VER}))
SUPPRESS_WARN := --no-warning TAILF_SUGGEST_COMBO
endif

YANG = $(wildcard tmp-yang/*.yang)
SUBMODULES = $(shell grep -l belongs-to tmp-yang/*.yang)
YANG_MODULES = $(filter-out $(SUBMODULES),$(YANG))

$(FXS): $(YANG)

JAVA_NAMESPACES = \
	$(shell echo $(YANG:yang/%.yang=java/src/$(JDIR)/namespaces/%.java) | \
		perl -pne 's/([a-zA-Z])-([a-zA-Z])/\1\U\2/g')

fxs:	ncsc-out/modules/fxs/tailf-ned-cisco-asa-stats.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-asa.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-asa-id.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-asa-meta.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-asa-oper.fxs

namespaces: $(JAVA_NAMESPACES)

ncsc-out/modules/fxs/tailf-ned-cisco-asa-stats.fxs:	yang/tailf-ned-cisco-asa-stats.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	        --ncs-skip-config \
	        --ncs-skip-template \
                --ncs-device-type cli-ned \
		${SUPPRESS_WARN} && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfNedCiscoAsaStats.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-asa.fxs:	tmp-yang/tailf-ned-cisco-asa.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	  	--ncs-skip-statistics \
                --ncs-device-type cli-ned \
		${SUPPRESS_WARN} && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfNedCiscoAsa.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-asa-id.fxs:	yang/tailf-ned-cisco-asa-id.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	  	--ncs-skip-statistics \
                --ncs-device-type cli-ned && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfNedCiscoAsaId.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-asa-meta.fxs:	yang/tailf-ned-cisco-asa-meta.yang
	$(NCSC) --yangpath yang -c $< -o $@ && \
          cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfNedCiscoAsaMeta.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-asa-oper.fxs:	yang/tailf-ned-cisco-asa-oper.yang
	$(NCSC) --yangpath yang -c $< -o $@ && \
          cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfNedCiscoAsaOper.java $@

javac: ../private-jar/nedcom.jar
	cd java && ant -q all

clean: nedcom_clean
	rm -rf ncsc-out/* ../load-dir/* ncsc-out/*
	rm -f ../private-jar/asa.jar
	rm -f java/src/$(JDIR)/$(NS)/*.java
	cd java && ant clean
	cd ../netsim && $(MAKE) clean
