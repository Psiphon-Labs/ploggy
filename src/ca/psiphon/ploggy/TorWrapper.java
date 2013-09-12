/*
 * Copyright (c) 2013, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.ploggy;

public class TorWrapper {

    public static final String SOCKS_PROXY_HOSTNAME = "127.0.0.1";
    
	private HiddenService.KeyMaterial mHiddenServiceIdentity;
	private int mWebServerPort;
	private int mSocksProxyPort; // TODO: where selected?
	
	public TorWrapper(HiddenService.KeyMaterial hiddenServiceIdentity, int webServerPort) {
		mHiddenServiceIdentity = hiddenServiceIdentity;
		mWebServerPort = webServerPort;
	}
	
	public void start() {
		
	}
	
	public void stop() {
		
	}
	
	public int getSocksProxyPort() {
	    return mSocksProxyPort;
	}

/*

    private static final String BUNDLED_BINARY_DATA_SUBDIRECTORY = "bundled-binaries";
    
    private static int getBundledBinaryResourceForPlatform(Context context)
    {
        // NOTE: no MIPS binaries are bundled at the moment
        if (0 == Build.CPU_ABI.compareTo("armeabi-v7a")) return R.raw.iptables_arm7;
        else if (0 == Build.CPU_ABI.compareTo("armeabi")) return R.raw.iptables_arm;
        else if (0 == Build.CPU_ABI.compareTo("x86")) return R.raw.iptables_x86;
        else if (0 == Build.CPU_ABI.compareTo("mips")) return R.raw.iptables_mips;
        return 0;
    }
    
    private static boolean extractBundledIpTables(Context context, int sourceResourceId, File targetFile)
    {
        try
        {
            InputStream zippedAsset = context.getResources().openRawResource(sourceResourceId);
            ZipInputStream zipStream = new ZipInputStream(zippedAsset);            
            zipStream.getNextEntry();
            InputStream bundledBinary = zipStream;
    
            FileOutputStream file = new FileOutputStream(targetFile);
    
            byte[] buffer = new byte[8192];
            int length;
            while ((length = bundledBinary.read(buffer)) != -1)
            {
                file.write(buffer, 0 , length);
            }
            file.close();
            bundledBinary.close();
    
            String chmodCommand = "chmod 700 " + targetFile.getAbsolutePath();
            Runtime.getRuntime().exec(chmodCommand).waitFor();
            
            return true;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (IOException e)
        {
            MyLog.e(R.string.TransparentProxyConfig_iptablesExtractFailed, MyLog.Sensitivity.NOT_SENSITIVE);
        }
        return false;
    }
    
    private static String getIpTablesPath(Context context, String binaryFilename)
            throws PsiphonTransparentProxyException
    {
        File binary = null;

        // Try to use bundled binary

        int bundledIpTablesResourceId = getBundledIpTablesResourceForPlatform(context);
        
        if (bundledIpTablesResourceId != 0)
        {        
            binary = new File(
                            context.getDir(BUNDLED_BINARY_DATA_SUBDIRECTORY, Context.MODE_PRIVATE),
                            binaryFilename);
            if (binary.exists())
            {
                return binary.getAbsolutePath();
            }
            else if (extractBundledIpTables(
                        context,
                        bundledIpTablesResourceId,
                        binary))
            {
                return binary.getAbsolutePath();
            }
            // else fall through to system binary case
        }
    }


*/
}
