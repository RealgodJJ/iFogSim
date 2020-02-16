package org.fog.application;

import java.util.List;

import org.fog.utils.TimeKeeper;

public class AppLoop {
    private int loopId;
    private List<String> modules;

    public AppLoop(List<String> modules) {
        setLoopId(TimeKeeper.getInstance().getUniqueId());
        setModules(modules);
    }

    public boolean hasEdge(String src, String dest) {
        for (int i = 0; i < modules.size() - 1; i++) {
            if (modules.get(i).equals(src) && modules.get(i + 1).equals(dest))
                return true;
        }
        return false;
    }

    public String getStartModule() {
        return modules.get(0);
    }

    public String getEndModule() {
        return modules.get(modules.size() - 1);
    }

    public boolean isStartModule(String module) {
        return getStartModule().equals(module);
    }

    public boolean isEndModule(String module) {
        return getEndModule().equals(module);
    }

    public String getNextModuleInLoop(String module) {
        String result = null;
        int i = 0;
        for (String mod : modules) {
            if (mod.equals(module)) {
                result = modules.get(i + 1);
                break;
            }
            i++;
        }
        return result;
    }

    //TODO: 应用循环是否含有这个应用模块
    public boolean hasModule(String module) {
        for (String moduleName : modules) {
            if (moduleName.equals(module))
                return true;
        }
        return false;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public int getLoopId() {
        return loopId;
    }

    public void setLoopId(int loopId) {
        this.loopId = loopId;
    }

}
