package io.jetbrains.opzio;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.PasswordAuthentication;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Dependencies {

    private static String pythonLocation = null;
    private static String resourcesLocation = null;

    public static boolean isPythonInstalled() {
        return Dependencies.getPythonLocation() != null;
    }

    public static String getResourcesLocation() {
        if (Dependencies.resourcesLocation == null) {
            if (isWindows()) {
                File appDataFolder = new File(System.getenv("APPDATA"));
                File resourcesFolder = new File(appDataFolder, "Opzio");
                Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
            } else {
                File userHomeDir = new File(System.getProperty("user.home"));
                File resourcesFolder = new File(userHomeDir, ".Opzio");
                Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
            }
        }
        return Dependencies.resourcesLocation;
    }

    public static String getPythonLocation() {
        if (Dependencies.pythonLocation != null)
            return Dependencies.pythonLocation;
        ArrayList<String> paths = new ArrayList<String>();
        paths.add(null);
        paths.add("/");
        paths.add("/usr/local/bin/");
        paths.add("/usr/bin/");
        if (isWindows()) {
            File resourcesLocation = new File(Dependencies.getResourcesLocation());
            paths.add(combinePaths(resourcesLocation.getAbsolutePath(), "python"));
            paths.add(getPythonFromRegistry(WinReg.HKEY_CURRENT_USER));
            paths.add(getPythonFromRegistry(WinReg.HKEY_LOCAL_MACHINE));
            for (int i=26; i<=50; i++) {
                paths.add(combinePaths("\\python" + i, "pythonw"));
                paths.add(combinePaths("\\Python" + i, "pythonw"));
            }
        }
        for (String path : paths) {
            if (runPython(combinePaths(path, "pythonw"))) {
                Dependencies.pythonLocation = combinePaths(path, "pythonw");
                break;
            } else if (runPython(combinePaths(path, "python"))) {
                Dependencies.pythonLocation = combinePaths(path, "python");
                break;
            }
        }
        if (Dependencies.pythonLocation != null) {
            Opzio.log.debug("Found python binary: " + Dependencies.pythonLocation);
        } else {
            Opzio.log.warn("Could not find python binary.");
        }
        return Dependencies.pythonLocation;
    }

    public static String getPythonFromRegistry(WinReg.HKEY hkey) {
        String path = null;
        if (isWindows()) {
            try {
                String key = "Software\\\\Wow6432Node\\\\Python\\\\PythonCore";
                for (String version : Advapi32Util.registryGetKeys(hkey, key)) {
                    path = Advapi32Util.registryGetStringValue(hkey, key + "\\" + version + "\\InstallPath", "");
                    if (path != null) {
                        break;
                    }
                }
            }  catch (Exception e) {
                Opzio.log.debug(e);
            }
            if (path == null) {
                try {
                    String key = "Software\\\\Python\\\\PythonCore";
                    for (String version : Advapi32Util.registryGetKeys(hkey, key)) {
                        path = Advapi32Util.registryGetStringValue(hkey, key + "\\" + version + "\\InstallPath", "");
                        if (path != null) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    Opzio.log.debug(e);
                }
            }
        }
        return path;
    }

    public static boolean isCLIInstalled() {
        File cli = new File(Dependencies.getCLILocation());
        return cli.exists();
    }

    public static boolean isCLIOld() {
        if (!Dependencies.isCLIInstalled()) {
            return false;
        }
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--version");
        try {
            Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            p.waitFor();
            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null) {
                output += s;
            }
            while ((s = stdError.readLine()) != null) {
                output += s;
            }
            Opzio.log.debug("opzio cli version check output: \"" + output + "\"");
            Opzio.log.debug("opzio cli version check exit code: " + p.exitValue());

            if (p.exitValue() == 0) {
                String cliVersion = latestCliVersion();
                Opzio.log.debug("Current cli version from GitHub: " + cliVersion);
                if (output.contains(cliVersion))
                    return false;
            }
        } catch (Exception e) {
            Opzio.log.warn(e);
        }
        return true;
    }

    public static String latestCliVersion() {
        String url = "https://raw.githubusercontent.com/opzio/opzio/master/wakatime/__about__.py";
        try {
            String aboutText = getUrlAsString(url);
            Pattern p = Pattern.compile("__version_info__ = \\('([0-9]+)', '([0-9]+)', '([0-9]+)'\\)");
            Matcher m = p.matcher(aboutText);
            if (m.find()) {
                return m.group(1) + "." + m.group(2) + "." + m.group(3);
            }
        } catch (Exception e) {
            Opzio.log.warn(e);
        }
        return "Unknown";
    }

    public static String getCLILocation() {
        return combinePaths(Dependencies.getResourcesLocation(), "opzio-sublime-master", "packages", "opzio", "cli.py");
    }

    public static void installCLI() {
        File cli = new File(Dependencies.getCLILocation());
        if (!cli.getParentFile().getParentFile().getParentFile().exists())
            cli.getParentFile().getParentFile().getParentFile().mkdirs();

        String url = "https://github.com/opzio/opzio-sublime/archive/master.zip";
        String zipFile = combinePaths(cli.getParentFile().getParentFile().getParentFile().getAbsolutePath(), "opzio-cli.zip");
        File outputDir = cli.getParentFile().getParentFile().getParentFile();

        // download opzio-master.zip file
        if (downloadFile(url, zipFile)) {

            // Delete old opzio-master directory if it exists
            File dir = cli.getParentFile().getParentFile();
            if (dir.exists()) {
                deleteDirectory(dir);
            }

            try {
                Dependencies.unzip(zipFile, outputDir);
                File oldZipFile = new File(zipFile);
                oldZipFile.delete();
            } catch (IOException e) {
                Opzio.log.warn(e);
            }
        }
    }

    public static void upgradeCLI() {
        Dependencies.installCLI();
    }

    public static void installPython() {
        if (isWindows()) {
            String pyVer = "3.5.2";
            String arch = "win32";
            if (is64bit()) arch = "amd64";
            String url = "https://www.python.org/ftp/python/" + pyVer + "/python-" + pyVer + "-embed-" + arch + ".zip";

            File dir = new File(Dependencies.getResourcesLocation());
            File zipFile = new File(combinePaths(dir.getAbsolutePath(), "python.zip"));
            if (downloadFile(url, zipFile.getAbsolutePath())) {

                File targetDir = new File(combinePaths(dir.getAbsolutePath(), "python"));

                // extract python
                try {
                    Dependencies.unzip(zipFile.getAbsolutePath(), targetDir);
                } catch (IOException e) {
                    Opzio.log.warn(e);
                }
                zipFile.delete();
            }
        }
    }

    public static boolean downloadFile(String url, String saveAs) {
        File outFile = new File(saveAs);

        // create output directory if does not exist
        File outDir = outFile.getParentFile();
        if (!outDir.exists())
            outDir.mkdirs();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            rbc = Channels.newChannel(downloadUrl.openStream());
            fos = new FileOutputStream(saveAs);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            return true;
        } catch (RuntimeException e) {
            Opzio.log.warn(e);
            try {
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[] { new LocalSSLTrustManager() }, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection)downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                fos = new FileOutputStream(saveAs);
                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                fos.close();
                return true;
            } catch (NoSuchAlgorithmException e1) {
                Opzio.log.warn(e1);
            } catch (KeyManagementException e1) {
                Opzio.log.warn(e1);
            } catch (IOException e1) {
                Opzio.log.warn(e1);
            }
        } catch (IOException e) {
            Opzio.log.warn(e);
        }

        return false;
    }

    public static String getUrlAsString(String url) {
        StringBuilder text = new StringBuilder();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        try {
            InputStream inputStream = downloadUrl.openStream();
            byte[] buffer = new byte[4096];
            while (inputStream.read(buffer) != -1) {
                text.append(new String(buffer, "UTF-8"));
            }
            inputStream.close();
        } catch (RuntimeException e) {
            Opzio.log.warn(e);
            try {
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[]{new LocalSSLTrustManager()}, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection) downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                byte[] buffer = new byte[4096];
                while (inputStream.read(buffer) != -1) {
                    text.append(new String(buffer, "UTF-8"));
                }
                inputStream.close();
            } catch (NoSuchAlgorithmException e1) {
                Opzio.log.warn(e1);
            } catch (KeyManagementException e1) {
                Opzio.log.warn(e1);
            } catch (UnknownHostException e1) {
                Opzio.log.warn(e1);
            } catch (IOException e1) {
                Opzio.log.warn(e1);
            }
        } catch (UnknownHostException e) {
            Opzio.log.warn(e);
        } catch (Exception e) {
            Opzio.log.warn(e);
        }

        return text.toString();
    }

    /**
     * Configures a proxy if one is set in ~/.opzio.cfg.
     */
    public static void configureProxy() {
        String proxyConfig = ConfigFile.get("settings", "proxy");
        if (proxyConfig != null && !proxyConfig.trim().equals("")) {
            try {
                URL proxyUrl = new URL(proxyConfig);
                String userInfo = proxyUrl.getUserInfo();
                if (userInfo != null) {
                    final String user = userInfo.split(":")[0];
                    final String pass = userInfo.split(":")[1];
                    Authenticator authenticator = new Authenticator() {
                        public PasswordAuthentication getPasswordAuthentication() {
                            return (new PasswordAuthentication(user, pass.toCharArray()));
                        }
                    };
                    Authenticator.setDefault(authenticator);
                }

                System.setProperty("https.proxyHost", proxyUrl.getHost());
                System.setProperty("https.proxyPort", Integer.toString(proxyUrl.getPort()));

            } catch (MalformedURLException e) {
                Opzio.log.error("Proxy string must follow https://user:pass@host:port format: " + proxyConfig);
            }
        }
    }

    private static boolean runPython(String path) {
        try {
            Opzio.log.debug(path + " --version");
            Process p = Runtime.getRuntime().exec(path + " --version");
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            p.waitFor();
            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null) {
                output += s;
            }
            while ((s = stdError.readLine()) != null) {
                output += s;
            }
            if (output != "")
                Opzio.log.debug(output);
            if (p.exitValue() != 0)
                throw new Exception("NonZero Exit Code: " + p.exitValue());

            return true;

        } catch (Exception e) {
            Opzio.log.debug(e.toString());
            return false;
        }
    }

    private static void unzip(String zipFile, File outputDir) throws IOException {
        if(!outputDir.exists())
            outputDir.mkdirs();

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry ze = zis.getNextEntry();

        while (ze != null) {
            String fileName = ze.getName();
            File newFile = new File(outputDir, fileName);

            if (ze.isDirectory()) {
                newFile.mkdirs();
            } else {
                FileOutputStream fos = new FileOutputStream(newFile.getAbsolutePath());
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            ze = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    private static void deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        path.delete();
    }

    public static boolean is64bit() {
        boolean is64bit = false;
        if (isWindows()) {
            is64bit = (System.getenv("ProgramFiles(x86)") != null);
        } else {
            is64bit = (System.getProperty("os.arch").indexOf("64") != -1);
        }
        return is64bit;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    public static String combinePaths(String... args) {
        File path = null;
        for (String arg : args) {
            if (arg != null) {
                if (path == null)
                    path = new File(arg);
                else
                    path = new File(path, arg);
            }
        }
        if (path == null)
            return null;
        return path.toString();
    }
}
