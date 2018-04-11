package moe.yo3explorer.vapor;

public class VaporException extends RuntimeException
{
    public VaporException(String s,Object... args)
    {
        super(String.format(s,args));
    }

    public VaporException(Throwable causedBy,String s,Object... args)
    {
        super(String.format(s,args),causedBy);
    }
}
