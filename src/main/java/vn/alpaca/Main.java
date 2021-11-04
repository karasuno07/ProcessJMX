package vn.alpaca;

import org.apache.commons.io.FilenameUtils;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String... args) throws ParserConfigurationException, IOException, SAXException {
        StandardJMeterEngine jmeter = new StandardJMeterEngine();
        JMeterUtils.setJMeterHome(Directory.JMETER_HOME);
        JMeterUtils.loadJMeterProperties(Directory.JMETER_HOME + "/bin/jmeter.properties");

        JMeterUtils.initLocale();

        SaveService.loadProperties();
        run(new File(Directory.INPUT_FOLDER));
    }

    public static void run(File folder) throws IOException {
        File[] listOfFiles = folder.listFiles();

        assert listOfFiles != null;
        for (File file : listOfFiles) {
            if (file.isFile()) {
                doAction(file);
            } else if (file.isDirectory()) {
                run(file);
            }
        }
    }

    public static void doAction(File file) throws IOException {
        HashTree testPlanTree = SaveService.loadTree(file);

        AtomicInteger count = new AtomicInteger(0);

        testPlanTree.values().forEach(threadGroup -> {

            threadGroup.values().forEach(controller -> {

                for (Map.Entry<Object, HashTree> entry : controller.entrySet()) {
                    HashTree sample = entry.getValue();
                    TransactionController subController = new TransactionController();

                    HashTree subTree = new ListedHashTree();

                    for (Object o : sample.getArray()) {
                        if (!(o instanceof HTTPSamplerProxy)) {
                            continue;
                        }
                        HTTPSamplerProxy proxy = (HTTPSamplerProxy) o;

                        boolean isFile = FilenameUtils.getExtension(proxy.getPath()).length() > 0;
                        boolean isDomainUrl = proxy.getPath().endsWith(".org") ||
                                proxy.getPath().endsWith(".com");
                        boolean isNotAlpacaDomain = !proxy.getDomain().contains(".alpaca.vn");
                        boolean isNotSocketDomain = !proxy.getDomain().contains("uat1-socket.alpaca.vn");
                        boolean isNotOptionsMethod = !proxy.getMethod().equalsIgnoreCase("options");
                        if (!isFile && !isDomainUrl && !isNotAlpacaDomain
                                && isNotSocketDomain && isNotOptionsMethod) {

                            if (!proxy.getDomain().contains("uat1-sso.alpaca.vn")
                                    && !proxy.getDomain().contains("uat1-api.alpaca.vn")
                                    && subTree.size() > 0) {
                                subController.setName("Sub Controller " + count.incrementAndGet());
                                subController.setEnabled(true);
                                subController.setProperty(TestElement.TEST_CLASS,
                                                          subController.getClass().getName());
                                subController.setProperty(TestElement.GUI_CLASS, "TransactionControllerGui");
                                subController.setProperty(
                                        new BooleanProperty("TransactionController.includeTimers", false));
                                sample.add(subController, subTree);
                                subController = new TransactionController();
                                subTree = new ListedHashTree();
                            }

                            HashTree tree = sample.get(o);
                            subTree.set(o, tree);
                        }
                        sample.remove(o);
                    }

                }
                count.set(0);
            });
        });

        File outputFile = new File(Directory.OUTPUT_FOLDER, file.getName());

        FileOutputStream fos = new FileOutputStream(outputFile, false);

        SaveService.saveTree(testPlanTree, fos);
    }

    public static final class Directory {
        public final static String INPUT_FOLDER = "E:\\Alpaca\\performance-test";
        public final static String OUTPUT_FOLDER = "E:\\Alpaca\\performance-test-edited";
        public final static String JMETER_HOME = "E:\\Alpaca\\apache-jmeter-5.4.1";
    }
}
