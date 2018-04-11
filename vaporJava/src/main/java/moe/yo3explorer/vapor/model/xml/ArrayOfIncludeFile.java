package moe.yo3explorer.vapor.model.xml;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.ArrayList;
import java.util.List;

public class ArrayOfIncludeFile
{
    @XStreamImplicit(itemFieldName = "IncludeFile")
    public List<IncludeFile> IncludeFile;
}
