package org.genepattern.server.webapp.jsf;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.validator.ValidatorException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.genepattern.server.UserPassword;
import org.genepattern.server.UserPasswordHome;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.util.GPConstants;

/**
 * Backing bean for pages/login.
 * 
 * @author jrobinso
 * 
 */
public class LoginBean extends AbstractUIBean {

    private static Logger log = Logger.getLogger(LoginBean.class);
    private String username;
    private String password;
    private boolean passwordRequired;

    private boolean unknownUser = false;
    private UIInput usernameComponent;

    public LoginBean() {
        String prop = System.getProperty("require.password").toLowerCase();
        passwordRequired = (prop.equals("true") || prop.equals("y") || prop.equals("yes"));
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return this.password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isUnknownUser() {
        return unknownUser;
    }

    /**
     * Submit the user / password. For now this uses an action listener since we
     * are redirecting to a page outside of the JSF framework. This should be
     * changed to an action to use jsf navigation in the future.
     * 
     * @param event --
     *            ignored
     */
    public void submitLogin(ActionEvent event) {

        try {
            assert username != null;
            assert password != null;
            HttpServletRequest request = getRequest();

            UserPassword up = (new UserPasswordHome()).findByUsername(username);
            if (up == null) {
                unknownUser = true;
            }
            else {
                if (password.equals(up.getPassword())) {
                    setUserAndRedirect(request, getResponse(), username);
                }
                else {
                    // We should never get here, the validatot (validateLogin)
                    // should catch this case.
                    log.error("Invalid password");
                }
            }
        }
        catch (UnsupportedEncodingException e) {
            log.error(e);
            throw new RuntimeException(e); // @TODO -- wrap in gp system
            // exeception.
        }
        catch (IOException e) {
            log.error(e);
            throw new RuntimeException(e); // @TODO -- wrap in gp system
            // exeception.
        }

    }

    public void validateLogin(FacesContext context, UIComponent component, Object value) {

        String name = (String) usernameComponent.getValue();

        // Get the password associated with the username from the database.
        UserPassword up = (new UserPasswordHome()).findByUsername(name);
        if (up == null) {
            // No user object with this name.  This is being handled at the submit stage, nothing to do here
        }
        else {
            Base64 decoder = new Base64();
            String actualPassword = new String(decoder.decode(up.getPassword().getBytes())).trim();
System.out.println(actualPassword);
System.out.println(value);
            if (!actualPassword.equals(value.toString().trim())) {
                String message = "Invalid password";
                System.out.println(message);
                FacesMessage facesMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, message, message);
                ((UIInput) component).setValid(false);
                throw new ValidatorException(facesMessage);
            }
        }
    }

    public UIInput getUsernameComponent() {
        return usernameComponent;
    }

    public void setUsernameComponent(UIInput passwordComponent) {
        this.usernameComponent = passwordComponent;
    }

    protected String getReferrer(HttpServletRequest request) {
        String referrer = request.getParameter("referrer");
        if (referrer == null || referrer.length() == 0) {
            referrer = request.getContextPath() + "/index.jsp";
        }
        return referrer;

    }

    protected void setUserAndRedirect(HttpServletRequest request, HttpServletResponse response, String username)
            throws UnsupportedEncodingException, IOException {
        request.setAttribute("userID", username);

        String userID = "\"" + URLEncoder.encode(username.replaceAll("\"", "\\\""), "utf-8") + "\"";
        Cookie cookie4 = new Cookie(GPConstants.USERID, userID);
        cookie4.setPath(getRequest().getContextPath());
        cookie4.setMaxAge(Integer.MAX_VALUE);
        getResponse().addCookie(cookie4);

        String referrer = getReferrer(request);
        referrer += (referrer.indexOf('?') > 0 ? "&" : "?");
        referrer += username;
        getResponse().sendRedirect(referrer);
    }

    public static void main(String[] args) {
        Base64 coder = new Base64();
        String test = "abc";
        String t1 = new String(coder.encode((new String(test)).getBytes()));

        String t2 = new String(coder.decode(t1.getBytes()));

        System.out.println(t1);
        System.out.println(t2);
        System.out.println(test.equals(t2));

    }

}
