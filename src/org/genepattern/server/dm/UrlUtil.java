/*******************************************************************************
 * Copyright (c) 2003, 2015 Broad Institute, Inc. and Massachusetts Institute of Technology.  All rights reserved.
 *******************************************************************************/
package org.genepattern.server.dm;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.genepattern.server.config.GpConfig;
import org.genepattern.server.config.ServerConfigurationFactory;
import org.genepattern.server.genomespace.GenomeSpaceFileHelper;
import org.genepattern.util.SemanticUtil;

import com.google.common.base.Strings;

/**
 * Utility methods for converting between Files (on the GP server file system) and URIs (to be be used as external references to those files).
 * 
 * @author pcarr
 */
public class UrlUtil {
    public static Logger log = Logger.getLogger(UrlUtil.class);

    /** @deprecated renamed to getBaseGpHref */
    public static String getGpUrl(final HttpServletRequest request) {
        return getBaseGpHref(request);
    }

    /**
     * Get the baseUrl of the web application, inclusive of the contextPath, but not including the trailing slash.
     * This is the URL for the root of your GenePattern site. 
     * 
     * For a default installation the contextPath is '/gp' and the baseUrl is,
     *     http://127.0.0.1:8080/gp
     * When the web app is installed in the root directory, the context path is the empty string,
     *     http://127.0.0.1:8080
     * 
     * This method uses the client HttpServletRequest to determine baseUrl. The client request in most cases
     * will be to a named host, rather than the internal 127.0.0.1 address. For example,
     *     requestURL=http://gpdev.broadinstitute.org/gp/rest/v1/jobs/0
     *     contextPath=/gp
     * Resolves to
     *     baseUrl= http://gpdev.broadinstitute.org/gp
     * 
     * @param req
     * @return
     */
    public static String getBaseGpHref(final HttpServletRequest req) {
        if (log.isDebugEnabled()) {
            log.debug("requestURL="+req.getRequestURL().toString());
            log.debug("contextPath="+req.getContextPath());
        }
        final String baseUrl=resolveBaseUrl(req.getRequestURL().toString(), req.getContextPath());
        if (log.isDebugEnabled()) {
            log.debug("baseUrl="+baseUrl);
        }
        return  baseUrl;
    }
    
    /**
     * resolve the baseUrl from the given requestUrlSpec and contextPath.
     * @param requestUrlSpec, e.g. 'http://127.0.0.1:8080/gp/jobResults/0/out.txt'
     * @param contextPath, e.g. '/gp'
     * 
     * @return the baseUrl of the server, e.g. 'http://127.0.0.1:8080/gp'
     */
    protected static String resolveBaseUrl(final String requestUrlSpec, String contextPath) {
        //adjust empty contextPath, must start with "/" in order to resolve it back to root 
        if (Strings.isNullOrEmpty(contextPath)) {
            contextPath="/";
        }
        final URI contextUrl = 
                URI.create(requestUrlSpec)
                    .resolve(contextPath);
        return  removeTrailingSlash( contextUrl.toString() );
    }

    /** @deprecated initialize from HttpServletRequest if possible */
    public static String getBaseGpHref(final GpConfig gpConfig) {
        return removeTrailingSlash(gpConfig.getGpUrl());
    }

    /**
     * @param gpConfig must be non-null
     * @param request can be null
     */
    public static String getBaseGpHref(final GpConfig gpConfig, final HttpServletRequest request) {
        if (request != null) {
            return getBaseGpHref(request);
        }
        else if (gpConfig != null) {
            return getBaseGpHref(gpConfig);
        }
        else {
            return getBaseGpHref(ServerConfigurationFactory.instance());
        }
    }

    /**
     * Append the base gpUrl to the relative uri, making sure to not duplicate the '/' character.
     * @param prefix, the base url (expected to not include the trailing slash)
     * @param suffix, the relative url (expected to start with a slash)
     * @return
     */
    public static String glue(final String prefix, final String suffix) {
        return removeTrailingSlash(Strings.nullToEmpty(prefix)) + 
                prependSlash(Strings.nullToEmpty(suffix));
    }

    /** prepend a slash '/' if and only if the input does not already start with one */
    protected static String prependSlash(final String in) {
        if (Strings.isNullOrEmpty(in)) {
            return "/";
        }
        if (in.startsWith("/")) {
            return in;
        }
        return "/"+in;
    }
    
    /** remove the trailing slash '/' if and only if the input ends with one */
    protected static String removeTrailingSlash(final String in) {
        if (in==null) {
            return "";
        }
        if (!in.endsWith("/")) {
            return in;
        }
        return in.substring(0, in.length()-1);
    }

    /**
     * default implementation of GpFilePath#getUrl for all internal URLs.
     * @param baseGpHref
     * @param gpFilePath_internal
     * @return
     * @throws MalformedURLException
     */
    public static URL getUrl(final String baseGpHref, final GpFilePath gpFilePath_internal) throws MalformedURLException {
        if (Strings.isNullOrEmpty(baseGpHref)) {
            throw new IllegalArgumentException("baseGpHref not set");
        }
        final String href=getHref(baseGpHref, gpFilePath_internal);
        return new URL(href);
    }
    
    /**
     * Get the callback href to the given GpFilePath file.
     * @param request
     * @param gpFilePath_internal
     * @return
     */
    public static String getHref(final HttpServletRequest request, final GpFilePath gpFilePath_internal) {
        if (request==null) {
            throw new IllegalArgumentException("request==null");
        }
        return getHref(getBaseGpHref(request), gpFilePath_internal);
    }

    public static String getHref(final GpConfig gpConfig, final GpFilePath gpFilePath_internal) {
        if (gpConfig==null) {
            throw new IllegalArgumentException("gpConfig==null");
        }
        return getHref(getBaseGpHref(gpConfig), gpFilePath_internal);
    }
    
    public static String getHref(final GpConfig gpConfig, final HttpServletRequest request, final GpFilePath gpFilePath_internal) {
        return getHref(getBaseGpHref(gpConfig, request), gpFilePath_internal);
    }

    /**
     * Get the callback href to the given GpFilePath file.
     * 
     * @param baseGpHref
     * @param gpFilePath_internal
     * @return
     */
    public static String getHref(final String baseGpHref, final GpFilePath gpFilePath_internal) {
        if (gpFilePath_internal==null) {
            throw new IllegalArgumentException("gpFilePath==null");
        }
        if (baseGpHref==null) {
            log.warn("baseGpHref==null, convert null to empty, relative url");
        }
        if (baseGpHref != null && baseGpHref.endsWith("/")) {
            log.warn("remove trailing slash from baseGpHref="+baseGpHref);
        }
        final String href=removeTrailingSlash(Strings.nullToEmpty(baseGpHref)) +
                gpFilePath_internal.getRelativeUri() + 
                (gpFilePath_internal.isDirectory() ? "/" : "");
        return href;
    }
    
    /**
     * Get the filename from URL, by analogy to File.getName(),
     * by default keepTrailingSlash is true.
     * 
     * @param url
     * @return
     */
    public static String getFilenameFromUrl(final URL url) {
         final boolean keepTrailingSlash=true;
         return getFilenameFromUrl(url, keepTrailingSlash);
    }
    
    /**
     * Get the filename from URL, by analogy to File.getName(),
     * optionally including the slash from the url path.
     * 
     * @param url
     * @param keepTrailingSlash, when true append trailing '/' from url.getPath.
     * @return
     */
    public static String getFilenameFromUrl(final URL url, final boolean keepTrailingSlash) {
        if (url==null) {
            return null;
        }
        String urlPath;
        try {
            urlPath=url.toURI().getPath();
        }
        catch (URISyntaxException e) {
            urlPath=url.getPath();
            if (log.isDebugEnabled()) {
                log.debug("error decoding path from url="+url+", use encoded path instead", e);
                log.debug("urlPath="+urlPath);
            }
        }  
        
        final boolean isGsFile=GenomeSpaceFileHelper.isGenomeSpaceFile(url);
        String filename=new File(urlPath).getName();
        // special-case for GenomeSpace linked to Google drive reference
        if (isGsFile) {
            filename=stripParen(filename);
        }
        if (keepTrailingSlash && urlPath.endsWith("/")) {
            filename += "/";
        }
        return filename;
    }
    
    /**
     * Extracts the file's kind from the URL and filename.  Uses a separate implementation from the one 
     * found in SemanticUtil because we need to handle GenomeSpace file conversions. Example queryString,
     * 
     *     "?dataformat=http://www.genomespace.org/datamanager/dataformat/gct"
     * 
     * @param url
     * @param filename
     * @return
     * @throws URISyntaxException 
     */
    public static String getKindFromUrl(final URL url, final String filename, final String extension) {
        final boolean isGsFile=GenomeSpaceFileHelper.isGenomeSpaceFile(url);
        if (isGsFile) {
            try {
                final String query=url.toURI().getQuery();
                if (query != null) {
                    final String[] pairs=query.split("&");
                    for(final String pair : pairs) {
                        final String[] param=pair.split("=");
                        if (param != null && param.length==2 && "dataformat".equalsIgnoreCase(param[0])) {
                            int idx=param[1].lastIndexOf("/dataformat/");
                            if (idx>=0) {
                                return param[1].substring(idx+"/dataformat/".length());
                            }
                        }
                    }
                }
            }
            catch (Throwable t) {
                log.error("Unexpected error getting query from url="+url, t);
            }
        } 
        // If no format parameter was found, extract from the filename
        return SemanticUtil.getKindForUrl(filename, extension);
    }

    /**
     * hack to strip out text in parenthesis for google drive files,
     * linked to from GenomeSpace.
     * Only matches pattern at end of filename.
     *     
     * @param filename, e.g. 'all_aml(0By2oidMlPWQtQ2RlQV8zd25Md1E)'
     * @return the filename, stripped of paren, e.g. 'all_aml'
     */
    protected static String stripParen(final String filename) {
        if (filename==null) {
            return null;
        }
        if (!filename.endsWith(")")) {
            // only if it ends with ')'
            return filename;
        }
        int parenthesisIndex = filename.lastIndexOf(("("));
        if (parenthesisIndex >= 0) {
            return filename.substring(0, parenthesisIndex);
        }
        return filename;
    }

    /** Converts a string into something you can safely insert into a URL. */
    public static String encodeURIcomponent(String str) {
        String encoded = str;
        try {
            encoded = URLEncoder.encode(str, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            log.error("UnsupportedEncodingException for enc=UTF-8", e);
            encoded = URLEncoder.encode(str);
        }
        
        //replace all '+' with '%20'
        encoded = encoded.replace("+", "%20");        
        return encoded;

    }
    
    /** Converts a string into something you can safely insert into a URL. */
    public static String decodeURIcomponent(final String encoded) {
        String decoded = encoded;
        try {
            decoded = URLDecoder.decode(encoded, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            log.error("UnsupportedEncodingException for enc=UTF-8", e);
            decoded = URLDecoder.decode(encoded);
        }
        catch (Throwable t) {
            log.error("Unexpected error decoding string="+encoded);
        }
        return decoded;
    }

    
    //alternative implementation which doesn't use any other java classes
    private static String encodeURIcomponent_orig(String s) {
        StringBuilder o = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (isUnsafe(ch)) {
                o.append('%');
                o.append(toHex(ch / 16));
                o.append(toHex(ch % 16));
            }
            else {
                o.append(ch);
            }
        }
        return o.toString();
    }
    
    //alternative implementation which uses standard java classes, File, URL, and URI
    private static String encodeURIcomponent_file(String name) {
        final File file=new File(name);
        final URI uri=file.toURI();
        try { 
            final URL url=uri.toURL();
            final String encodedPath=url.toExternalForm();
            int beginIndex=encodedPath.lastIndexOf("/");
            ++beginIndex;
            if (beginIndex<0) {
                beginIndex=0;
            }
            final String encodedName=encodedPath.substring(beginIndex);
            return encodedName;
        }
        catch (MalformedURLException e) {
            log.error(e);
        }
        return name;
    }

    private static char toHex(int ch) {
        return (char)(ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch) {
        if (ch > 128 || ch < 0) {
            return true;
        }
        return " %$&+,/:;=?@<>#%\\".indexOf(ch) >= 0;
    }

    /**
     * Encode the File into a valid URI path component.
     * @param file
     * @return
     */
    public static String encodeFilePath(File file) {
        if (file == null) {
            log.error("Invalid null arg");
            return "";
        }
        if (file.getParent() == null) {
            return encodeURIcomponent( file.getName() );
        }
        else {
            return encodeFilePath( file.getParentFile() ) + "/" + encodeURIcomponent( file.getName() );
        }
    }

}
