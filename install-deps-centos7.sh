#!/bin/bash
# run this as root

yum -y update
yum -y install java-1.8.0-openjdk-devel
PTEST=`grep JAVA_HOME /root/.bashrc`
if [[ ! -z "$PTEST" ]] ; then
	echo "JAVA_HOME already set to " $PTEST
	exit 1
fi
echo "export JAVA_HOME=/usr/lib/jvm/java-1.8.0" >> ~/.bashrc
curl https://bintray.com/sbt/rpm/rpm | tee /etc/yum.repos.d/bintray-sbt-rpm.repo
yum -y install sbt
curl --silent --location https://rpm.nodesource.com/setup_10.x | bash -
yum -y install nodejs
curl --silent --location https://dl.yarnpkg.com/rpm/yarn.repo | tee /etc/yum.repos.d/yarn.repo
rpm --import https://dl.yarnpkg.com/rpm/pubkey.gpg
yum -y install yarn
yum install -y https://download.postgresql.org/pub/repos/yum/11/redhat/rhel-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm
tee /etc/yum.repos.d/timescale_timescaledb.repo <<EOL
[timescale_timescaledb]
name=timescale_timescaledb
baseurl=https://packagecloud.io/timescale/timescaledb/el/7/\$basearch
repo_gpgcheck=1
gpgcheck=0
enabled=1
gpgkey=https://packagecloud.io/timescale/timescaledb/gpgkey
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
metadata_expire=300
EOL
yum update -y
curl -L "https://github.com/docker/compose/releases/download/1.23.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
