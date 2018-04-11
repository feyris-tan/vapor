package moe.yo3explorer.vapor;

import moe.yo3explorer.vapor.model.GameInfo;

import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

@ManagedBean
public class IndexController
{
    public LinkedList<GameInfo> getAllPlayableGames()
    {
        return DataFetcher.getInstance().getAllPlayableGames();
    }

    public String getUsername()
    {
        HttpServletRequest req = (HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest();
        return req.getUserPrincipal().getName();
    }

    public String getHostname()
    {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "some machine on the internet";
        }
    }


}
