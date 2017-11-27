all: fxs javac
	cd ../netsim && $(MAKE) all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

JAVA_PACKAGE = com.tailf.examples.router
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/$(NS)

DIRS = ncsc-out ../load-dir ../private-jar ../shared-jar java/src/$(JDIR)/$(NS)

SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)

$(DIRS):
	mkdir -p $@

fxs: $(DIRS) ncsc-out/modules

ncsc-out/modules: $(SRC)
	$(NCSC) --ncs-compile-bundle yang \
                 --ncs-device-dir ncsc-out \
                 --ncs-device-type netconf && \
        cp ncsc-out/modules/fxs/*.fxs ../load-dir;
	for f in `echo ../load-dir/*.fxs`; do \
           n=`basename $$f | sed 's/\.fxs//'`; \
	   $(NCSC) $(JFLAGS)/$${n}.java $$f  || exit 1; \
        done
	touch -m ncsc-out/modules

javac: $(DIRS)
	cd java && ant -q all
	cp java/build/jar/*-ns.jar ../shared-jar
	cp java/build/jar/*.jar ../private-jar

clean:
	rm -rf $(DIRS) java/src
	cd java && ant -q clean || true
	cd ../netsim && $(MAKE) clean || true
