using System;
using System.IO;

namespace vaporimport
{
    public static class MemoryStreamExtension
    {
        public static byte[] Sanitize(this MemoryStream ms)
        {
            byte[] msBuffer = ms.GetBuffer();
            int length = (int)ms.Position;
            byte[] result = new byte[length];
            Array.Copy(msBuffer, 0, result, 0, length);
            return result;
        }
    }
}