package common.agent.script;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 *  on 2016/6/24.
 *
 * Java 脚本编译器
 */
public class JavaScriptCompiler
{
    private final static Logger LOGGER = LoggerFactory.getLogger(JavaScriptCompiler.class);

    /**
     *  用于编译java源码的内部结构
     */
    private class JavaSourceFromString extends SimpleJavaFileObject
    {
        private final String m_code;

        /**
         * Construct a JavaSourceFromString of the given name and with the
         * given code.
         */
        protected JavaSourceFromString(String name, String code)
        {
            super(URI.create("string:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.m_code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors)
        {
            return m_code;
        }
    }

    private String m_compileClassPath;      // 编译Java脚本需要的路径
    private String m_compileOutputPath;     // 编译后输出的路径
    private List<String> m_compileOption;   // 编译选项

    /**
     *  构建编译路径
     */
    public void InitClassPath(String outputPath)
    {
        // 先清空
        m_compileClassPath = null;

        // 准备编译路径
        // 当前类本身使用的classLoader,从jar包运行，默认为URLClassLoader
        URLClassLoader classLoader = (URLClassLoader)getClass().getClassLoader();

        // 从当前类的编译环境中解析出编译需要的路径
        StringBuilder builder = new StringBuilder();
        for (URL url : classLoader.getURLs())
        {
            String p = url.getFile();
            builder.append(p).append(File.pathSeparator);
        }
        m_compileClassPath = builder.toString();

        // 编译后输出路径
        m_compileOutputPath = outputPath;
        _PrepareScriptPath(m_compileOutputPath);

        // 准备编译选项
        m_compileOption = new ArrayList<>();
        m_compileOption.add("-encoding");
        m_compileOption.add("UTF-8");
        m_compileOption.add("-classpath");
        m_compileOption.add(this.m_compileClassPath);
        m_compileOption.add("-d");
        m_compileOption.add(this.m_compileOutputPath); // javac编译结果输出到classFilePath目录中

        LOGGER.info("-----------------------脚本编译器选项------------------------------");
        LOGGER.info(m_compileOption.toString());
        LOGGER.info("-----------------------------------------------------------------");

     }

    /**
     *  通过字节码，编译出IScript
     * @param name     待编译的类全名
     * @param bytes     待编译的类字节码
     * @return
     */
    public Class<?> BuildScript(String name, byte[] bytes) throws IOException, IllegalAccessException, InstantiationException
    {
        Class<?> clazz = _JavaCodeToObject(name, new String(bytes, "UTF-8"));
        if (clazz != null) {
            return clazz;
        } else {
            LOGGER.error("clazz == null , 编译脚本失败:" + name);
            return null;
        }
    }

    /**
     *   编译Java源代码
     * @param name
     * @param code
     * @return
     */
    private Class<?> _JavaCodeToObject(String name, String code) throws IOException
    {
        // 准备编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

        // 准备源文件
        List<JavaFileObject> jFiles = new ArrayList<>();
        jFiles.add(new JavaSourceFromString(name.replace('.', '/'), code));

        // 开始编译
        Class<?> result = null;
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, m_compileOption, null, jFiles);
        if (!task.call())
        {
            String error = "";
            for (Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics())
            {
                error = error + _CompileErrorString(diagnostic);
            }

            LOGGER.error(error);
        }
        else
        {
            // 成功编译成.class文件，通过classLoader加载class
            // 每次new一个classloader
//            result = new CJavaScriptLoader().LoadScriptClass(m_compileOutputPath + "/" + name.replace('.', '/') + ".class",  name);
            try {
                result = new JavaScriptLoader().loadClass(name);
            }catch (Exception e){
                LOGGER.error(e.getMessage().toString(),e);
            }

        }

        fileManager.close();
        return result;
    }

    /**
     *   格式化编译错误信息
     * @param diagnostic
     * @return
     */
    private String _CompileErrorString(Diagnostic<?> diagnostic)
    {
        StringBuilder res = new StringBuilder();
        res.append("Code:[").append(diagnostic.getCode()).append("]\n");
        res.append("Kind:[").append(diagnostic.getKind()).append("]\n");
        res.append("Position:[").append(diagnostic.getPosition()).append("]\n");
        res.append("Start Position:[").append(diagnostic.getStartPosition()).append("]\n");
        res.append("End Position:[").append(diagnostic.getEndPosition()).append("]\n");
        res.append("Source:[").append(diagnostic.getSource()).append("]\n");
        res.append("Message:[").append(diagnostic.getMessage(null)).append("]\n");
        res.append("LineNumber:[").append(diagnostic.getLineNumber()).append("]\n");
        res.append("ColumnNumber:[").append(diagnostic.getColumnNumber()).append("]\n");
        return res.toString();
    }

    /**
     * 脚本路径初始化
     */
    private void _PrepareScriptPath(String outputPath)
    {
        File file = new File(outputPath);
        if (file.exists())
        {
            _DeleteScriptPath(file);
        }
        file.mkdirs();
    }

    private void _DeleteScriptPath(File file)
    {
        if (file.isFile())
        {
            file.delete();
        }
        else if (file.isDirectory())
        {
            File[] files = file.listFiles();
            for (int a = 0; a < files.length; ++a)
            {
                _DeleteScriptPath(files[a]);
            }
        }
    }

}
