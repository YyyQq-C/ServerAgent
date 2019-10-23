package common.agent.script;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class JavaScriptLoader extends ClassLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptLoader.class);

    /**
     * 加载脚本
     *
     * @param
     * @return
     * @throws ClassNotFoundException
     */
    @Override
    public Class<?> loadClass(String classname) throws ClassNotFoundException
    {
        try
        {
            String classFilePath = "./scriptBin/" + classname.replace('.', '/') + ".class";

            // 查看class目录中是否存在,即是是脚本,是脚本则由自己加载
            File file = new File(classFilePath);
            if (!file.isFile())
            {
                return super.loadClass(classname);
            }

            if (!file.canRead())
            {
                LOGGER.error("class文件路径指向不是一个合法的文件或者该文件当前不可读: " + classFilePath);
                return null;
            }

            // 由于脚本相互显示引用,可能已经被加载了.(内部类)
            Class<?> _class = this.findLoadedClass(classname);
            if (_class != null)
            {
                return _class;
            }

            byte[] bytes = loadClassData(classname);
            return this.defineClass(classname, bytes, 0, bytes.length);
        }
        catch (IOException e)
        {
            LOGGER.error(e.getMessage(), e);
            throw new ClassNotFoundException(classname);
        }
    }

    /**
     * 读取字节码
     *
     * @param name
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private byte[] loadClassData(String name) throws FileNotFoundException, IOException, ClassNotFoundException
    {
        int readCount = 0;
        String classFileName = "./scriptBin" + "/" + name.replace('.', '/') + ".class";
        FileInputStream in = null;
        ByteArrayOutputStream buffer = null;
        try
        {
            in = new FileInputStream(classFileName);
            buffer = new ByteArrayOutputStream();
            while ((readCount = in.read()) != -1)
            {
                buffer.write(readCount);
            }
            return buffer.toByteArray();
        }
        finally
        {
            try
            {
                if (in != null)
                {
                    in.close();
                }
                if (buffer != null)
                {
                    buffer.close();
                }
            }
            catch (IOException e)
            {
                LOGGER.error(e.getMessage(), e);
                throw new ClassNotFoundException(name);
            }
        }
    }
}
