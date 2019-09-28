package com.xmlcalabash.extensions;

import com.xmlcalabash.core.XMLCalabash;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.AxisNodes;
import com.xmlcalabash.util.ProcessMatch;
import com.xmlcalabash.util.ProcessMatchingNodes;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.TreeWriter;
import com.xmlcalabash.util.XProcURIResolver;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static com.xmlcalabash.core.XProcConstants.c_result;

@XMLCalabash(
        name = "cx:pygments",
        type = "{http://xmlcalabash.com/ns/extensions}pygments")

public class Pygments extends DefaultStep implements ProcessMatchingNodes {
    private static final QName _language = new QName("language");
    private static final QName _exec = new QName("exec");
    private static final String ns_html = "http://www.w3.org/1999/xhtml";
    private static final QName h_span = new QName("", ns_html, "span");
    private String exec = "pygmentize";
    private String pygmentize = null;
    private List<String> cmdline = new ArrayList<String>();

    private Hashtable<QName,String> params = new Hashtable<QName, String> ();
    private ProcessMatch matcher = null;
    private ReadablePipe source = null;
    private WritablePipe result = null;
    private String format = "html";      // No other formats are supported
    private String language = null;

    private static final String library_xpl = "http://xmlcalabash.com/extension/steps/pygments.xpl";
    private static final String library_url = "/com/xmlcalabash/extensions/pygments/library.xpl";

    public Pygments(XProcRuntime runtime, XAtomicStep step) {
        super(runtime, step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        source = pipe;
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void setParameter(QName name, RuntimeValue value) {
        params.put(name, value.getString());
    }

    public void reset() {
        source.resetReader();
        result.resetWriter();
    }

    public void run() throws SaxonApiException {
        super.run();

        if (getOption(_language) != null) {
            language = getOption(_language).getString();
        }
        if (getOption(_exec) != null) {
            exec = getOption(_exec).getString();
        }

        if (!findPygmentize()) {
            throw new XProcException("cx:pygments cannot find '" + exec + "' on path.");
        }

        cmdline.add(pygmentize);
        cmdline.add("-f");
        cmdline.add(format);
        if (language != null) {
            cmdline.add("-l");
            cmdline.add(language);
        }

        for (QName key : params.keySet()) {
            if ("".equals(key.getNamespaceURI())) {
                cmdline.add("-P");
                cmdline.add(key.getLocalName() + "=" + params.get(key));
            }
        }

        XdmNode doc = source.read();
        RuntimeValue matchExpr = new RuntimeValue("text()", doc);

        matcher = new ProcessMatch(runtime, this);
        matcher.match(doc, matchExpr);

        result.write(matcher.getResult());
    }

    public static void configureStep(XProcRuntime runtime) {
        XProcURIResolver resolver = runtime.getResolver();
        URIResolver uriResolver = resolver.getUnderlyingURIResolver();
        URIResolver myResolver = new StepResolver(uriResolver);
        resolver.setUnderlyingURIResolver(myResolver);
    }

    private boolean findPygmentize() {
        // I'm trying to make this Windows-compatible, but I'm not testing it there...
        String paths[] = System.getenv("PATH").split(File.pathSeparator);
        for (String path : paths) {
            if (pygmentize == null && !"".equals(path)) {
                File pexec = new File(path +  File.separatorChar + exec);
                if (pexec.exists() && pexec.canExecute()) {
                    pygmentize = pexec.getAbsolutePath();
                }
            }
        }

        return pygmentize != null;
    }

    private void addHtmlNamespace(TreeWriter tree, XdmNode node) {
        if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                addHtmlNamespace(tree, cnode);
            }
        } else if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            QName newName = new QName("", ns_html, node.getNodeName().getLocalName());
            tree.addStartElement(newName);
            tree.addAttributes(node);

            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                addHtmlNamespace(tree, cnode);
            }
            tree.addEndElement();
        } else {
            tree.addSubtree(node);
        }
    }

    @Override
    public void processText(XdmNode node) throws SaxonApiException {
        try {
            ProcessBuilder builder = new ProcessBuilder(cmdline);
            Process process = builder.start();
            OutputStream os = process.getOutputStream();
            PrintStream ps = new PrintStream(os);
            ps.print(node.getStringValue());
            ps.close();
            os.close();

            ProcessOutputReader stdoutReader = new ProcessOutputReader(process.getInputStream(), true);
            ProcessOutputReader stderrReader = new ProcessOutputReader(process.getErrorStream(), false);

            Thread stdoutThread = new Thread(stdoutReader);
            Thread stderrThread = new Thread(stderrReader);

            stdoutThread.start();
            stderrThread.start();

            int rc = 0;
            try {
                rc = process.waitFor();
                stdoutThread.join();
                stderrThread.join();
            } catch (InterruptedException tie) {
                throw new XProcException(tie);
            }

            XdmNode errResult = stderrReader.getResult();
            XdmNode outResult = S9apiUtils.getDocumentElement(stdoutReader.getResult());

            if (!"".equals(errResult.getStringValue().trim())) {
                System.err.println(errResult.getStringValue());
            }

            if (rc != 0) {
                throw XProcException.stepError(64);
            }

            // Pygmnents doesn't put the elements in a namespace, *sigh*
            TreeWriter newTree = new TreeWriter(runtime);
            newTree.startDocument(node.getBaseURI());
            addHtmlNamespace(newTree, outResult);
            newTree.endDocument();
            outResult = S9apiUtils.getDocumentElement(newTree.getResult());

            // The outResult is now a <result> element containing the markup that Pygments
            // produced. Step one, get rid of the <result> wrapper.

            // Find the child element; there has to be one...
            XdmNode div = null;
            for (XdmNode cnode : new AxisNodes(outResult, Axis.CHILD, AxisNodes.SIGNIFICANT)) {
                if (cnode.getNodeKind() == XdmNodeKind.ELEMENT) {
                    div = cnode;
                    break;
                }
            }

            // Pygments usually returns a div containing a pre. That's not ideal in cases
            // where markup from XML is being styled. (The pre is probably already provided
            // by the surrounding markup and it means that you can't run Pygments over
            // inline code samples. If the markup returned by Pygments consisted of a div
            // containing a pre; drop the div and the pre. (If some other markup was returned,
            // it's because the user specified options that created a table or something;
            // just return all that unchanged.)

            // Find the pre; if there is one
            XdmNode pre = null;
            if (div != null && "div".equals(div.getNodeName().getLocalName())) {
                for (XdmNode cnode : new AxisNodes(div, Axis.CHILD, AxisNodes.SIGNIFICANT)) {
                    if (cnode.getNodeKind() == XdmNodeKind.ELEMENT) {
                        if ("pre".equals(cnode.getNodeName().getLocalName())) {
                            pre = cnode;
                        }
                        break;
                    }
                }
            }

            newTree = new TreeWriter(runtime);
            newTree.startDocument(node.getBaseURI());

            if (pre == null) {
                // Some option (e.g., linenos) resulted in different wrapper markup...
                newTree.addSubtree(div);
            } else {
                newTree.addStartElement(h_span);
                newTree.addAttributes(div);
                newTree.startContent();
                for (XdmNode cnode : new AxisNodes(pre, Axis.CHILD, AxisNodes.ALL)) {
                    newTree.addSubtree(cnode);
                }
                newTree.addEndElement();
            }

            newTree.endDocument();
            outResult = newTree.getResult();

            matcher.addSubtree(outResult);
        } catch (IOException ioe) {
            throw new XProcException(ioe);
        }
    }

    @Override
    public boolean processStartDocument(XdmNode node) throws SaxonApiException {
        throw new XProcException("Pygments error; attempted to process start document");
    }

    @Override
    public void processEndDocument(XdmNode node) throws SaxonApiException {
        throw new XProcException("Pygments error; attempted to process end document");
    }

    @Override
    public boolean processStartElement(XdmNode node) throws SaxonApiException {
        throw new XProcException("Pygments error; attempted to process start element");
    }

    @Override
    public void processAttribute(XdmNode node) throws SaxonApiException {
        throw new XProcException("Pygments error; attempted to process attribute");
    }

    @Override
    public void processEndElement(XdmNode node) throws SaxonApiException {
        throw new XProcException("Pygments error; attempted to process end element");
    }

    @Override
    public void processComment(XdmNode node) throws SaxonApiException {
        throw new XProcException("Highlighter error; attempted to process comment");
    }

    @Override
    public void processPI(XdmNode node) throws SaxonApiException {
        throw new XProcException("Highlighter error; attempted to process processing instruction");
    }

    private class ProcessOutputReader implements Runnable {
        private InputStream is;
        private boolean asXML;
        private TreeWriter tree;

        public ProcessOutputReader(InputStream is, boolean asXML) {
            this.is = is;
            this.asXML = asXML;

            tree = new TreeWriter(runtime);
        }

        public XdmNode getResult() {
            return tree.getResult();
        }

        public void run() {
            tree.startDocument(step.getNode().getBaseURI());

            tree.addStartElement(c_result);
            tree.startContent();

            if (asXML) {
                XdmNode doc = runtime.parse(new InputSource(is));
                tree.addSubtree(doc);
            } else {
                // If we're not wrapping the lines, a buffered reader doesn't work. It can't
                // tell the difference between a file with a trailing EOL and one without.
                try {
                    InputStreamReader r = new InputStreamReader(is);
                    char[] buf = new char[1000];
                    int len = r.read(buf,0,buf.length);
                    while (len >= 0) {
                        if (len == 0) {
                            Thread.sleep(1000);
                            continue;
                        }
                        String s = new String(buf,0,len);
                        tree.addText(s);
                        len = r.read(buf,0,buf.length);
                    }
                } catch (IOException ioe) {
                    throw new XProcException(ioe);
                } catch (InterruptedException ie) {
                    // who cares?
                }
            }

            tree.addEndElement();
            tree.endDocument();
        }
    }

    private static class StepResolver implements URIResolver {
        Logger logger = LoggerFactory.getLogger(Pygments.class);
        URIResolver nextResolver = null;

        public StepResolver(URIResolver next) {
            nextResolver = next;
        }

        @Override
        public Source resolve(String href, String base) throws TransformerException {
            try {
                URI baseURI = new URI(base);
                URI xpl = baseURI.resolve(href);
                if (library_xpl.equals(xpl.toASCIIString())) {
                    URL url = Pygments.class.getResource(library_url);
                    logger.debug("Reading library.xpl for cx:pygments from " + url);
                    InputStream s = Pygments.class.getResourceAsStream(library_url);
                    if (s != null) {
                        SAXSource source = new SAXSource(new InputSource(s));
                        return source;
                    } else {
                        logger.info("Failed to read " + library_url + " for cx:pygments");
                    }
                }
            } catch (URISyntaxException e) {
                // nevermind
            }

            if (nextResolver != null) {
                return nextResolver.resolve(href, base);
            } else {
                return null;
            }
        }
    }
}