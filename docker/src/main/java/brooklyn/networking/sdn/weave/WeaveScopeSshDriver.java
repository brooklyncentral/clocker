package brooklyn.networking.sdn.weave;

import com.google.common.collect.Lists;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

import java.util.List;

/**
 * Created by graememiller on 09/09/2015.
 */
public class WeaveScopeSshDriver extends AbstractSoftwareProcessSshDriver implements WeaveScopeDriver  {


    public WeaveScopeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public String getWeaveScopeCommand() {
        return Os.mergePathsUnix(getInstallDir(), "scope");
    }


    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
    }




    @Override
    public boolean isRunning() {
        //Script checks how many docker process called weave scope are running, if there is more than one it exits 0, otherwise exits 1
        return newScript(CHECK_RUNNING)
                .uniqueSshConnection()
                .body.append("a=$(docker ps -f=name=weavescope -q | wc -l); if [[ $a -gt 0 ]]; then exit 0; else exit 1; fi;")
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(LAUNCHING)
                .body.append(getWeaveScopeCommand()+" stop")
                .execute();
    }

    @Override
    public void install() {
        List<String> commands = Lists.newLinkedList();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(resolver.getTargets(), getWeaveScopeCommand()));
        commands.add("chmod 755 " + getWeaveScopeCommand());

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }


    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();
    }

    @Override
    public void launch() {
        newScript(LAUNCHING)
                .body.append(getWeaveScopeCommand()+" launch")
                .execute();
    }
}
