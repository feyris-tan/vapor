package moe.yo3explorer.vapor.model;

import java.util.Date;

public class GameInfo
{
    private String title;
    private GameInfo resourceDependency;
    private int id;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public GameInfo getResourceDependency() {
        return resourceDependency;
    }

    public void setResourceDependency(GameInfo resourceDependency) {
        this.resourceDependency = resourceDependency;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLinkCoutcome()
    {
        return String.format("play/%d",getId());
    }
}
