using System;
using System.Security.Cryptography;
using System.Text;

public class Hashdeep : HashAlgorithm
{
    private HashAlgorithm _md5;
    private HashAlgorithm _sha256;
    private long _length;

    public override void Initialize()
    {
        if (_md5 != null)
        {
            _md5.Dispose();
            _sha256.Dispose();
        }
        _md5 = MD5.Create();
        _sha256 = SHA256.Create();
        _length = 0;
    }

    protected override void HashCore(byte[] array, int ibStart, int cbSize)
    {
        if (_md5 == null) Initialize();
        _md5.TransformBlock(array, ibStart, cbSize, array, ibStart);
        _sha256.TransformBlock(array, ibStart, cbSize, array, ibStart);
        _length += cbSize;
    }

    protected override byte[] HashFinal()
    {
        byte[] empty = new byte[0];
        _md5.TransformFinalBlock(empty, 0, 0);
        _sha256.TransformFinalBlock(empty, 0, 0);

        byte[] a = BitConverter.GetBytes(_length);
        byte[] b = _md5.Hash;
        byte[] c = _sha256.Hash;

        byte[] result = new byte[8 + b.Length + c.Length];


        Array.Copy(a, 0, result, 0, 8);
        Array.Copy(b, 0, result, 8, b.Length);
        Array.Copy(c, 0, result, 8 + b.Length, c.Length);

        return result;
    }

    public static String GetFingerprint(byte[] bytes)
    {
        Hashdeep hashdeep = new Hashdeep();
        byte[] hash = hashdeep.ComputeHash(bytes);

        StringBuilder sb = new StringBuilder();
        foreach (byte t in hash)
        {
            sb.Append(t.ToString("x2"));
        }
        return sb.ToString();
    }
}