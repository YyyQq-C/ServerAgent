package common.agent.agent;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;
import common.ServerAgent;
import common.agent.script.ScriptManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.tools.attach.LinuxAttachProvider;
import sun.tools.attach.LinuxVirtualMachine;
import sun.tools.attach.WindowsAttachProvider;
import sun.tools.attach.WindowsVirtualMachine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by YongQianCheng on 2017/9/22.
 * <p>
 * Agent
 */
public class Agent
{
    private static final Logger LOGGER               = LoggerFactory.getLogger(Agent.class);
    private static final String FileSufFix           = ".class";
    private static final String SeparatorPoint       = ".";
    private static final String FileSeparatorWindows = "\\";
    private static final String FileSeparatorLinux   = "/";

    private static WindowsVirtualMachine winVirtualMachine;       // 当前运行jvm windows
    private static LinuxVirtualMachine   LinVirtualMachine;       // 当前运行jvm linux
    private static String                pid;                     // pid
    private static Instrumentation       instrumentation;
    private static String                agentPath;               // 代理路径
    private static String                javaPath;                // java文件路径
    private static String                classPath;               // class文件路径
    private static boolean               isWindows;               // 是否是windows
    private static ScriptManager        scriptManager;           // java编译器

    static
    {
        String os = System.getProperty("os.name");
        if (os.substring(0, os.length() > 3 ? 3 : os.length()).equalsIgnoreCase("win"))
            isWindows = true;

        String name = ManagementFactory.getRuntimeMXBean().getName();
        pid = name.split("@")[0];
        try
        {
            agentPath = getJarPath();
        }
        catch (FileNotFoundException e)
        {
            LOGGER.error(e.getMessage(), e);
        }
        javaPath = System.getProperty("user.dir") + File.separator + "agent" + File.separator + "agentJava" + File.separator;
        classPath = System.getProperty("user.dir") + File.separator + "agent" + File.separator + "agentClass" + File.separator;
    }

    /**
     * 初始化
     */
    public static void initialize()
    {
        try
        {
            init();

            scriptManager = new ScriptManager();
            scriptManager.Initialize(javaPath, classPath);

            // 初始化加载class
            reloadClass(classPath, true);
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static void init() throws Exception
    {
        try
        {
            List var1 = AttachProvider.providers();
            if (var1.size() == 0)
            {
                throw new AttachNotSupportedException("no providers installed");
            }
            else
            {
                AttachNotSupportedException var2 = null;
                Iterator var3 = var1.iterator();

                // 根据系统加载不同的jvm
                while (var3.hasNext())
                {
                    AttachProvider var4 = (AttachProvider) var3.next();
                    if (isWindows && var4 instanceof LinuxAttachProvider)
                        continue;
                    else if (!isWindows && var4 instanceof WindowsAttachProvider)
                        continue;

                    try
                    {
                        if (isWindows)
                        {
                            winVirtualMachine = (WindowsVirtualMachine) var4.attachVirtualMachine(pid);
                            winVirtualMachine.loadAgent(agentPath);
                        }
                        else
                        {
                            LinVirtualMachine = (LinuxVirtualMachine) var4.attachVirtualMachine(pid);
                            LinVirtualMachine.loadAgent(agentPath);
                        }

                        instrumentation = ServerAgent.getInstrumentation();
                        Preconditions.checkNotNull(instrumentation, "instrumentation must not be null.");
                        return;
                    }
                    catch (AttachNotSupportedException var6)
                    {
                        var2 = var6;
                    }
                }

                throw var2;
            }
        }
        catch (Exception e)
        {
            throw new Exception("init agent error:" + e.getMessage());
        }
    }

    /**
     * 获取ServerAgent jar包路径
     *
     * @return
     */
    public static String getJarPath() throws FileNotFoundException
    {
        // ServerAgent是jar文件内容
        URL url = ServerAgent.class.getProtectionDomain().getCodeSource().getLocation();
        String filePath = null;
        try
        {
            // 转化为utf-8编码
            filePath = URLDecoder.decode(url.getPath(), "utf-8");
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage(), e);
            throw new FileNotFoundException("common.ServerAgent jar not found.");
        }

        File file = new File(filePath);

        //得到绝对路径
        filePath = file.getAbsolutePath();
        return filePath;
    }

    /**
     * 销毁
     * 断开与虚拟机的连接
     */
    public static void destroy()
    {
        try
        {
            if (winVirtualMachine != null)
                winVirtualMachine.detach();

            if (LinVirtualMachine != null)
                LinVirtualMachine.detach();

            if (instrumentation != null)
                instrumentation = null;
        }
        catch (Exception e)
        {
            LOGGER.error("destroy virtualMachine error.", e);
        }
    }

    /**
     * 重载java文件
     * @param fileName java文件路径 只能填写agentJava后面的路径/ 如果是单个文件 不需要带文件后缀名
     * @param isDirectory 是否是文件夹
     */
    public static void agent(String fileName, boolean isDirectory)
    {
        try
        {
            if (isWindows)
                fileName = fileName.replace(FileSeparatorLinux, File.separator);
            else
                fileName = fileName.replace(FileSeparatorWindows, File.separator);

            if (fileName.endsWith(File.separator))
                fileName = fileName.substring(0, fileName.length() - 1);

            if (isDirectory)
            {
                // 删除旧的class文件
                String classFullName = classPath + fileName;
                deleteFile(classFullName);

                ArrayList<String> list = getAllJavaFiles(javaPath + File.separator + fileName);
                if (list == null)
                    return;

                int index = fileName.lastIndexOf(File.separator);
                String rootName = index > -1 ? fileName.substring(0, index) + SeparatorPoint : "";
                rootName = rootName.replace(File.separator, SeparatorPoint);
                for (int i = 0; i < list.size(); i++)
                {
                    scriptManager.LoadScript(rootName +list.get(i));
                }

                reloadClass(classPath + fileName, true);
            }
            else
            {
                int index = fileName.lastIndexOf(File.separator);
                String rootName = index > -1 ? fileName.substring(0, index) : "";
                _deleteClassFile(classPath + rootName, fileName);
                scriptManager.LoadScript(fileName.replace(File.separator, "."));
                reloadClass(classPath + rootName, true);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("agent error.", e);
        }
    }

    /**
     * 替换class文件 只支持class文件不支持java文件
     * @param fileName 1.包含class文件的路径(logic/team/),可以为空 表示加载所有class
     *                 2.单个class文件(logic.team.CTeam)
     * @param isDirectory 是否是文件夹形式 文件夹形式会加载文件夹下面所有class文件
     *
     * eg: 替换CTeam,将CTeam.class放在agent/agentClass/ 路径下。
     *                    fileName: logic.team.CTeam, isDirectory: false；
     *     同时替换CTeam、CTeamManager, 将CTeam.class、CTeamManager放在agent/agentClass/logic/team/ 路径下,路径不存在需要新建。
     *                    fileName:logic/team, isDirectory: true。
     *
     */
    public static void reloadClass(String fileName, boolean isDirectory)
    {
        try
        {
            init();

            if (isDirectory)
            {
                // 处理分隔符
                // windows
                if (isWindows)
                    fileName = fileName.replace(FileSeparatorLinux, File.separator);
                else
                    fileName = fileName.replace(FileSeparatorWindows, File.separator);

                List<ClassDefinition> classDefinition = getDirectoryClassDefinition(fileName);
                for (int i = 0; i < classDefinition.size(); i++)
                {
                    ClassDefinition definition = classDefinition.get(i);
                    instrumentation.redefineClasses(definition);
                    LOGGER.info("reload class -->> " + definition.getDefinitionClass().getName());
                }
            }
            else
            {
                if (fileName.isEmpty())
                    throw new NullPointerException("fileName must not be null.");

                // 单个文件处理
                fileName = fileName.replace(FileSeparatorLinux, SeparatorPoint);
                fileName = fileName.replace(FileSeparatorWindows, SeparatorPoint);

                Class<?> clazzName = Class.forName(fileName);
                String className = classPath + fileName.replace(SeparatorPoint, File.separator) + FileSufFix;
                byte[] bytesFromFile = Files.toByteArray(new File(className));
                ClassDefinition classDefinition = new ClassDefinition(clazzName, bytesFromFile);
                instrumentation.redefineClasses(classDefinition);
                LOGGER.info("reload class -->> " + fileName);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("reloadClass error.", e);
        }
        finally
        {
//            destroy();
        }
    }

    /**
     * 递归处理文件夹下面所有待替换的class
     * @param fileName 文件夹
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static List<ClassDefinition> getDirectoryClassDefinition(String fileName) throws ClassNotFoundException, IOException
    {
        List<ClassDefinition> classDefList = new ArrayList<>();
        // 添加分隔符
        if (!fileName.isEmpty() && !fileName.endsWith(File.separator))
            fileName += File.separator;

        // 补全文件夹全路径
        String path = fileName;
        if (!fileName.startsWith(classPath))
            path = classPath + fileName;

        File file = new File(path);
        if (file.isDirectory())
        {
            // 文件列表
            String[] list = file.list();
            if (list == null || list.length == 0)
            {
                return classDefList;
            }

            for (int i = 0; i < list.length; i++)
            {
                String name = list[i];

                // 判断是否是子目录
                String childFileName = path + name;
                File childFile = new File(childFileName);
                if (childFile.isDirectory())
                {
                    classDefList.addAll(getDirectoryClassDefinition(childFileName));
                    continue;
                }

                // 只处理以'.class'结尾文件
                if (!name.substring(name.lastIndexOf(SeparatorPoint), name.length()).equals(FileSufFix))
                    continue;

                // 子目录需要再处理一次class路径
                if (fileName.startsWith(classPath))
                    fileName = fileName.substring(classPath.length(), fileName.length());

                // old class path
                String clazzName = fileName.replace(File.separator, SeparatorPoint) + name.substring(0, name.lastIndexOf(SeparatorPoint));
                // 截取文件分隔符
                while (clazzName.startsWith(SeparatorPoint))
                    clazzName = clazzName.substring(1, clazzName.length());

                Class<?> clazz = Class.forName(clazzName);

                // 新的class路径
                byte[] bytesFromFile = Files.toByteArray(new File(childFileName));
                ClassDefinition classDefinition = new ClassDefinition(clazz, bytesFromFile);
                classDefList.add(classDefinition);
            }
        }
        else
        {
            LOGGER.error("fileName must be directory." + fileName);
        }

        return classDefList;
    }

    public static ArrayList<String> getAllJavaFiles(String javaFilePath)
    {
        File file = new File(javaFilePath);
        if (!file.exists() || !file.isDirectory())
        {
            LOGGER.warn("指定的脚本路径不存在：" + javaFilePath);
            return null;
        }

        ArrayList<String> list = new ArrayList<>();

        // 递归所有的文件，解析出类全名
        _ProcessFile(file, list);

        if (list.isEmpty())
            return null;
        return list;
    }

    /**
     * 递归遍历所有文件，同时解析类全名
     *
     * @param file
     * @param list
     */
    private static void _ProcessFile(File file, ArrayList<String> list)
    {
        String pkg = "";
        File[] files = file.listFiles();
        String rootPath = file.getName();
        if (files == null || files.length <= 0)
        {
            LOGGER.error("file.listFiles 找不到脚本：" + file.getAbsolutePath());
            return;
        }

        for (int a = 0; a < files.length; ++a)
        {
            String filename = pkg = files[a].getName();
            if (files[a].isFile() && filename.endsWith(".java"))
            {
                String className = filename.substring(0, filename.indexOf("."));
                list.add(rootPath + "." + className);
            }
            else
            {
                if (files[a].isDirectory())
                {
                    _ProcessFile(rootPath, files[a], pkg, list);
                }
            }
        }
    }

    /**
     * 递归遍历所有文件，同时解析类全名
     *
     * @param file  文件名
     * @param pkg 包名
     * @param list  结果集
     */
    private static void _ProcessFile(String rootPath, File file, String pkg, ArrayList<String> list)
    {

        File[] files = file.listFiles();
        if (files == null || files.length <= 0)
        {
            LOGGER.info(String.format("加载脚本子目录文件,该目录[%s]为空" ,file.getAbsolutePath()));
            return;
        }

        for (int a = 0; a < files.length; ++a)
        {
            String filename = files[a].getName();
            if (files[a].isFile() && filename.endsWith(".java"))
            {
                String className = filename.substring(0, filename.indexOf("."));
                String classFullName = pkg + "." + className;
                list.add(rootPath + "." + classFullName);
            }
            else
            {
                String pk = pkg+"."+ files[a].getName();
                _ProcessFile(rootPath, files[a], pk, list);
            }
        }
    }

    /**
     * 删除class文件
     * @param path
     * @param name
     */
    private static void _deleteClassFile(String path, String name)
    {
        File file = new File(path);
        if (!file.exists())
            return;

        File[] files = file.listFiles();
        if (files == null)
            return;

        name = name.substring(name.lastIndexOf(File.separator) + 1, name.length());
        for (File _file : files)
        {
            if (_file.isDirectory())
                continue;

            if (_file.getName().equals(name + ".class"))
            {
                _file.delete();
                continue;
            }

            if (_file.getName().startsWith(name + "$"))
            {
                _file.delete();
            }
        }
    }

    /**
     * 删除文件
     * @param fileName 文件路径
     */
    public static void deleteFile(String fileName)
    {
        if (fileName == null || fileName.isEmpty())
            return;

        File file = new File(fileName);
        if (!file.exists())
            return;

        if (file.isDirectory())
        {
            File[] files = file.listFiles();
            if (files == null)
                return;

            for (int i = 0; i < files.length; i++)
            {
                deleteFile(files[i]);
            }
        }

        file.delete();
    }

    /**
     * 删除文件
     * @param file 文件/文件夹
     */
    public static void deleteFile(File file)
    {
        if (file == null)
            return;

        if (!file.exists())
            return;

        if (file.isDirectory())
        {
            File[] files = file.listFiles();
            if (files == null)
                return;

            for (int i = 0; i < files.length; i++)
            {
                deleteFile(files[i]);
            }
        }

        file.delete();
    }

    public static String getClassPath()
    {
        return classPath;
    }

    public static String getJavaPath()
    {
        return javaPath;
    }

    public static void addJavaPathSuffix(String suffix)
    {
        Agent.javaPath += suffix;
    }

    public static void main(String[] args) throws InterruptedException
    {
//        SyncTest.begin();
//        for (int i = 1; i < 100; i ++)
//        {
            reloadClass("server/agent/test/", true);
//            Thread.sleep(5000);
//        }
    }
}
