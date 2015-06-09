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
  config.vm.network "forwarded_port", guest: 4000, host: 4000

  config.vm.synced_folder "~/.brooklyn", "/home/vagrant/.brooklyn"

  config.vm.provision "shell", inline: <<-SHELL
    sudo apt-get update
    apt-get install -y openjdk-7-jdk
  SHELL
end
