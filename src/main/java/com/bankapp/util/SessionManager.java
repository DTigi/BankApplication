package com.bankapp.util;

import com.bankapp.model.Account;
import com.bankapp.model.Client;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpSession;

@Component
public class SessionManager {
    private static final String LOGGED_IN_CLIENT = "loggedInClient";
    private static final String RECIPIENT_CLIENT = "recipientClient";
    private static final String RECIPIENT_ACCOUNT = "recipientAccount";

    private HttpSession getSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attr.getRequest().getSession(true);
    }

    public void login(Client client) {
        getSession().setAttribute(LOGGED_IN_CLIENT, client);
    }

    public Client getLoggedInClient() {
        return (Client) getSession().getAttribute(LOGGED_IN_CLIENT);
    }

    public void logout() {
        HttpSession session = getSession();
        session.removeAttribute(LOGGED_IN_CLIENT);
        session.removeAttribute(RECIPIENT_CLIENT);
        session.removeAttribute(RECIPIENT_ACCOUNT);
    }

    public boolean isLoggedIn() {
        return getLoggedInClient() != null;
    }

    public void setRecipientClient(Client client) {
        getSession().setAttribute(RECIPIENT_CLIENT, client);
    }

    public void setRecipientAccount(Account account) {
        getSession().setAttribute(RECIPIENT_ACCOUNT, account);
    }

    public Client getRecipientClient() {
        return (Client) getSession().getAttribute(RECIPIENT_CLIENT);
    }

    public Account getRecipientAccount() {
        return (Account) getSession().getAttribute(RECIPIENT_ACCOUNT);
    }

    public void clearRecipientData() {
        HttpSession session = getSession();
        session.removeAttribute(RECIPIENT_CLIENT);
        session.removeAttribute(RECIPIENT_ACCOUNT);
    }
}