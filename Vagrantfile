# Copyright 2015 by Cloudsoft Corporation Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

Vagrant.configure(2) do |config|
  config.vm.define "clocker"
  config.vm.box = "trusty"
  config.vm.box_url = "https://cloud-images.ubuntu.com/vagrant/trusty/current/trusty-server-cloudimg-amd64-vagrant-disk1.box"
  config.vm.provider "virtualbox" do |vb|
    vb.name = "clocker"
    vb.memory = "1024"
  end

  # brooklyn/clocker console
  config.vm.network "forwarded_port", guest: 8081, host: 8081

  # remote debug
  #config.vm.network "forwarded_port", guest: 4000, host: 4000

  config.vm.synced_folder "~/.brooklyn", "/home/vagrant/.brooklyn"

  config.vm.provision "shell", inline: <<-SHELL
    sudo apt-get update
    sudo apt-get install -y openjdk-7-jdk
  SHELL
end

# vim:ts=2:sw=2:ft=ruby:
