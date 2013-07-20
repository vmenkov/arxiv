#!/bin/sh
# ant.conf (Ant 1.7.x)
# JPackage Project <http://www.jpackage.org/>

# Validate --noconfig setting in case being invoked
# from pre Ant 1.6.x environment
if [ -z "$no_config" ] ; then
  no_config=true
  echo "no_config=true"
fi

# Setup ant configuration
if $no_config ; then
  # Disable RPM layout
  rpm_mode=false
else
  # Use RPM layout
  rpm_mode=true

  # ANT_HOME for rpm layout
  ANT_HOME=/usr/share/ant
fi

  echo ANT_HOME=$ANT_HOME
