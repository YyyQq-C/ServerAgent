package common.agent.script;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class ScriptManager
{
    // 单件接口
    public static ScriptManager getInstance()
    {
        return Singleton.INSTANCE.getManager();
    }

    private enum Singleton
    {
        INSTANCE;

        ScriptManager manager;

        Singleton()
        {
            this.manager = new ScriptManager();
        }

        ScriptManager getManager()
        {
            return manager;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptManager.class);


    private String m_javaFilePath;                                   // Java脚本源文件路径
    private JavaScriptCompiler m_compiler;                           // Java脚本编译器

    /**
     * 初始化java脚本路径
     *
     * @param javaFilePath java文件路径
     * @param javaClassPath 编译后的class文件路径
     * @throws Exception
     */
    public void Initialize(String javaFilePath, String javaClassPath) throws Exception
    {
        m_javaFilePath = javaFilePath;
        // Java脚本编译器初始化
        if (m_compiler == null)
        {
            m_compiler = new JavaScriptCompiler();
        }

        m_compiler.InitClassPath(javaClassPath);


        // 先加载一次所有脚本
        LoadScript();
    }

    /**
     * 加载所有脚本
     *
     * @throws Exception
     */
    public void LoadScript() throws Exception
    {
        ArrayList<String> allJavaFile = _GetAllJavaFiles(m_javaFilePath);
        if (allJavaFile == null || allJavaFile.isEmpty())
        {
            LOGGER.warn("指定的脚本路径[" + m_javaFilePath + "]下没有 '*.java' 文件!");
            return;
        }

        for (int a = 0; a < allJavaFile.size(); ++a)
        {
            LoadScript(allJavaFile.get(a));
        }

        LOGGER.info("load script finish.");

    }

    /**
     * 通过类名加载一个脚本，如果脚本已经存在且未发生改变，不会重新加载。否则会被覆盖，如果不存在，则会新加一个.
     *
     * @param name
     * @return
     * @throws Exception
     */
    public boolean LoadScript(String name) throws Exception
    {
        byte[] bytes = _ReadJavaSourceFile(name);
        if (bytes == null || bytes.length == 0)
        {
            LOGGER.error("读取java文件异常：" + name);
            return false;
        }

        // 此处try catch了 会造成脚本出错后还服务器还继续启动.
        // 全部检查正确后整体替换
        Class<?> clazz = null;
        try
        {
            clazz = m_compiler.BuildScript(name, bytes);
        }
        catch (Exception e)
        {
            //LOGGER.error("编译脚本异常: " + e, e);
            //return false;
            throw new Exception("编译脚本异常: "  + e.getMessage());
        }

        if (clazz == null)
        {
            throw new Exception(String.format("脚本编译失败，返回class 对象为null : %s", name));
            // return false;
        }

        return true;
    }

    private byte[] _ReadJavaSourceFile(String name)
    {
        String fullJavaFilePath = m_javaFilePath + "/" + _ParseJavaFilePathByClassname(name);
        LOGGER.info("读取java源文件: [" + fullJavaFilePath + "]");

        File file = new File(fullJavaFilePath);
        if (!file.isFile() || !file.canRead())
        {
            LOGGER.error("java源文件异常，可能不是一个有效的文件路径或者该文件当前不可读: " + fullJavaFilePath);
            return null;
        }

        try (InputStream stream = new FileInputStream(fullJavaFilePath))
        {
            byte[] bytes = new byte[(int) file.length()];
            stream.read(bytes);
            return bytes;
        }
        catch (IOException e)
        {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 从脚本类的全名解析脚本文件路径
     *
     * @param name
     * @return
     */
    private String _ParseJavaFilePathByClassname(String name)
    {
        return name.replace('.', '/') + ".java";
    }

    /**
     * 返回该路径下的所有脚本文件
     *
     * @param javaFilePath
     * @return
     */
    private ArrayList<String> _GetAllJavaFiles(String javaFilePath)
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
    private void _ProcessFile(File file, ArrayList<String> list)
    {
        String pkg = "";
        File[] files = file.listFiles();
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
                list.add(className);
            }
            else
            {
                if (files[a].isDirectory())
                {
                    _ProcessFile(files[a], pkg, list);
                }
            }
        }
    }

    /**
     * 递归遍历所有文件，同时解析类全名
     *
     * @param file 文件名
     * @param pkg 包名
     * @param list 结果集
     */
    private void _ProcessFile(File file, String pkg, ArrayList<String> list)
    {

        File[] files = file.listFiles();
        if (files == null || files.length <= 0)
        {
            LOGGER.info(String.format("加载脚本子目录文件,该目录[%s]为空", file.getAbsolutePath()));
            return;
        }

        for (int a = 0; a < files.length; ++a)
        {
            String filename = files[a].getName();
            if (files[a].isFile() && filename.endsWith(".java"))
            {
                String className = filename.substring(0, filename.indexOf("."));
                String classFullName = pkg + "." + className;
                list.add(classFullName);
            }
            else
            {
                String pk = pkg + "." + files[a].getName();
                _ProcessFile(files[a], pk, list);
            }
        }
    }
}
