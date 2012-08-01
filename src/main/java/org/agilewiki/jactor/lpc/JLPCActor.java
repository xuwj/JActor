/*
 * Copyright 2011 Bill La Forge
 *
 * This file is part of AgileWiki and is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License (LGPL) as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 * or navigate to the following url http://www.gnu.org/licenses/lgpl-2.1.txt
 *
 * Note however that only Scala, Java and JavaScript files are being covered by LGPL.
 * All other files are covered by the Common Public License (CPL).
 * A copy of this license is also included and can be
 * found as well at http://www.opensource.org/licenses/cpl1.0.txt
 */
package org.agilewiki.jactor.lpc;

import org.agilewiki.jactor.*;
import org.agilewiki.jactor.apc.*;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsDestination;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsQueue;
import org.agilewiki.jactor.events.EventQueue;
import org.agilewiki.jactor.factory.ActorFactory;
import org.agilewiki.jactor.factory.Requirement;
import org.agilewiki.jactor.stateMachine.ExtendedResponseProcessor;
import org.agilewiki.jactor.stateMachine._SMBuilder;

import java.util.ArrayList;

/**
 * <p>
 * An actor which implements Local Procedure Calls (LPC)
 * and mostly works synchronously.
 * Actors need to implement the processRequest method.
 * </p>
 * <pre>
 * public class Multiply {
 *     public int a;
 *     public int b;
 * }
 *
 * public class Multiplier extends JLPCActor {
 *
 *     public Multiplier(Mailbox mailbox) {
 *         super(mailbox);
 *     }
 *
 *     protected void processRequest(Object req, RP rp)
 *             throws Exception {
 *         Multiply m = (Multiply) req;
 *         rp.process(new Integer(m.a * m.b));
 *     }
 * }
 * </pre>
 */
abstract public class JLPCActor implements TargetActor, RequestProcessor, RequestSource {
    /**
     * The factory, or null.
     */
    private ActorFactory factory;

    /**
     * The inbox and outbox of the actor.
     */
    private Mailbox mailbox;

    /**
     * The parent actor, for dependency injection.
     */
    private JLPCActor parent;

    /**
     * Create a LiteActor
     *
     * @param mailbox A mailbox which may be shared with other actors.
     */
    final public void initialize(final Mailbox mailbox) throws Exception {
        initialize(mailbox, null, null);
    }

    /**
     * Create a LiteActor
     *
     * @param mailbox A mailbox which may be shared with other actors.
     * @param parent  The parent actor.
     */
    final public void initialize(final Mailbox mailbox, Actor parent) throws Exception {
        initialize(mailbox, parent, null);
    }

    /**
     * Create a LiteActor
     *
     * @param mailbox A mailbox which may be shared with other actors.
     * @param parent  The parent actor.
     * @param factory The factory.
     */
    public void initialize(final Mailbox mailbox, Actor parent, ActorFactory factory) throws Exception {
        if (mailbox == null) throw new IllegalArgumentException("mailbox may not be null");
        if (this.mailbox != null) throw new IllegalStateException("already initialized");
        this.mailbox = mailbox;
        this.factory = factory;
        Requirement[] requirements = requirements();
        if (requirements == null || requirements.length == 0) {
            this.parent = (JLPCActor) parent;
            return;
        }
        int i = 0;
        while (i < requirements.length) {
            Requirement requirement = requirements[i];
            Request request = requirement.request;
            if (parent == null || request.getTargetActor(parent) == null) {
                ActorFactory actorFactory = requirement.actorFactory;
                parent = actorFactory.newActor(mailbox, parent);
            }
            i += 1;
        }
        this.parent = (JLPCActor) parent;
    }

    /**
     * Returns the actor's parent.
     *
     * @return The actor's parent, or null.
     */
    @Override
    final public JLPCActor getParent() {
        return parent;
    }

    /**
     * Returns A matching ancestor from the parent chain.
     *
     * @param ancestorClass A class which the ancestor is an instanceof.
     * @return The matching ancestor, or null.
     */
    @Override
    final public JLPCActor getAncestor(Class ancestorClass) {
        if (parent == null)
            return null;
        if (ancestorClass.isInstance(parent))
            return parent;
        return parent.getAncestor(ancestorClass);
    }

    /**
     * Returns the actor's requirements.
     *
     * @return The actor's requirents.
     */
    protected Requirement[] requirements()
            throws Exception {
        return null;
    }

    /**
     * Returns the actor type.
     *
     * @return The actor type, or null.
     */
    @Override
    final public String getActorType() {
        if (factory == null)
            return null;
        return factory.actorType;
    }

    /**
     * Returns the factory.
     *
     * @return The factory, or null.
     */
    @Override
    final public ActorFactory getFactory() {
        return factory;
    }

    /**
     * Returns the exception handler.
     *
     * @return The exception handler.
     */
    @Override
    final public ExceptionHandler getExceptionHandler() {
        return mailbox.getExceptionHandler();
    }

    /**
     * Assign an exception handler.
     *
     * @param exceptionHandler The exception handler.
     */
    @Override
    final public void setExceptionHandler(final ExceptionHandler exceptionHandler) {
        mailbox.setExceptionHandler(exceptionHandler);
    }

    /**
     * A notification that there are incoming requests and responses that are ready for processing.
     */
    @Override
    final public void haveEvents() {
        mailbox.dispatchEvents();
    }

    /**
     * Process a wrapped request.
     *
     * @param request The wrapped request.
     * @throws Exception An exception thrown while processing the request.
     */
    @Override
    final public void processRequest(final JARequest request) throws Exception {
        if (request.isEvent())
            _processRequest(request.getUnwrappedRequest(), JANoResponse.nrp);
        else _processRequest(request.getUnwrappedRequest(), new RP() {
            @Override
            public void processResponse(Object response) {
                JARequest old = mailbox.getCurrentRequest();
                mailbox.setCurrentRequest(request);
                mailbox.response(request, response);
                mailbox.setCurrentRequest(old);
            }
        });
    }

    /**
     * Returns the actor's mailbox.
     *
     * @return The actor's mailbox.
     */
    @Override
    final public Mailbox getMailbox() {
        return mailbox;
    }

    /**
     * Enqueues the response in the responder's outbox.
     *
     * @param eventQueue   The responder's outbox.
     * @param japcResponse The wrapped response to be enqueued.
     */
    @Override
    final public void responseFrom(final BufferedEventsQueue<JAMessage> eventQueue,
                                   final JAResponse japcResponse) {
        eventQueue.send(mailbox, japcResponse);
    }

    /**
     * Sends a request to a mailbox.
     *
     * @param destination The mailbox which is to receive the request.
     * @param japcRequest The wrapped request to be sent.
     */
    @Override
    final public void send(final BufferedEventsDestination<JAMessage> destination,
                           final JARequest japcRequest) {
        mailbox.send(destination, japcRequest);
    }

    /**
     * Set the initial capacity for buffered outgoing messages.
     *
     * @param initialBufferCapacity The initial capacity for buffered outgoing messages.
     */
    @Override
    final public void setInitialBufferCapacity(final int initialBufferCapacity) {
        mailbox.setInitialBufferCapacity(initialBufferCapacity);
    }

    /**
     * Wraps and enqueues an unwrapped request in the requester's inbox.
     *
     * @param apcRequestSource The originator of the request.
     * @param request          The request to be sent.
     * @param rp               The request processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    @Override
    final public void acceptRequest(APCRequestSource apcRequestSource,
                                    Request request,
                                    RP rp)
            throws Exception {
        RequestSource rs = (RequestSource) apcRequestSource;
        Mailbox sourceMailbox = rs.getMailbox();
        if (sourceMailbox == mailbox) {
            syncSend(rs, request, rp);
            return;
        }
        if (sourceMailbox == null) {
            asyncSend(rs, request, rp);
            return;
        }
        acceptOtherRequest(sourceMailbox, rs, request, rp);
    }

    private void acceptOtherRequest(
            Mailbox sourceMailbox,
            RequestSource rs,
            Request request,
            RP rp) throws Exception {
        EventQueue<ArrayList<JAMessage>> eventQueue = mailbox.getEventQueue();
        EventQueue<ArrayList<JAMessage>> srcController = sourceMailbox.getEventQueue().getController();
        if (eventQueue.getController() == srcController) {
            syncSend(rs, request, rp);
            return;
        }
        if (!eventQueue.acquireControl(srcController)) {
            asyncSend(rs, request, rp);
            return;
        }
        try {
            syncSend(rs, request, rp);
        } finally {
            mailbox.dispatchEvents();
            mailbox.sendPendingMessages();
            eventQueue.relinquishControl();
        }
    }

    private void asyncSend(final RequestSource rs,
                           final Request request,
                           final RP rp)
            throws Exception {
        final JARequest jaRequest = new JARequest(
                rs,
                this,
                request,
                rp);
        rs.send(mailbox, jaRequest);
    }

    private void syncSend(final RequestSource rs,
                          final Request request,
                          final RP rp)
            throws Exception {
        final JARequest syncRequest = new JARequest(rs, JLPCActor.this, request, rp);
        ExtendedResponseProcessor extendedResponseProcessor = new ExtendedResponseProcessor() {
            @Override
            public void processResponse(Object response) throws Exception {
                JARequest oldCurrent = mailbox.getCurrentRequest();
                ExceptionHandler oldExceptionHandler = mailbox.getExceptionHandler();
                if (!async) {
                    sync = true;
                    if (!syncRequest.isActive()) {
                        return;
                    }
                    syncRequest.inactive();
                    syncRequest.restoreSourceMailbox();
                    if (response instanceof Exception) {
                        mailbox.setCurrentRequest(oldCurrent);
                        mailbox.setExceptionHandler(oldExceptionHandler);
                        throw (Exception) response;
                    }
                    try {
                        rp.processResponse(response);
                    } catch (Exception e) {
                        throw new TransparentException(e);
                    }
                } else {
                    mailbox.processResponse(syncRequest, response);
                    syncRequest.restoreSourceMailbox();
                }
                mailbox.setCurrentRequest(oldCurrent);
                mailbox.setExceptionHandler(oldExceptionHandler);
            }
        };

        mailbox.setCurrentRequest(syncRequest);
        try {
            _processRequest(request, extendedResponseProcessor);
            if (!extendedResponseProcessor.sync) {
                extendedResponseProcessor.async = true;
                syncRequest.restoreSourceMailbox();
            }
        } catch (TransparentException tx) {
            throw (Exception) tx.getCause();
        } catch (Exception ex) {
            if (!extendedResponseProcessor.sync)
                syncRequest.restoreSourceMailbox();
            throw ex;
        }
    }

    /**
     * Wraps and enqueues an unwrapped request in the requester's inbox.
     *
     * @param apcRequestSource The originator of the request.
     * @param request          The request to be sent.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    @Override
    final public void acceptEvent(APCRequestSource apcRequestSource,
                                  Request request)
            throws Exception {
        RequestSource rs = (RequestSource) apcRequestSource;
        ExceptionHandler sourceExceptionHandler = rs.getExceptionHandler();
        Mailbox sourceMailbox = rs.getMailbox();
        if (sourceMailbox == mailbox) {
            syncSendEvent(rs, request, sourceExceptionHandler);
            return;
        }
        if (sourceMailbox == null) {
            asyncSendEvent(rs, request);
            return;
        }
        EventQueue<ArrayList<JAMessage>> eventQueue = mailbox.getEventQueue();
        EventQueue<ArrayList<JAMessage>> srcController = sourceMailbox.getEventQueue().getController();
        if (eventQueue.getController() == srcController) {
            syncSendEvent(rs, request, sourceExceptionHandler);
            return;
        }
        if (!eventQueue.acquireControl(srcController)) {
            asyncSendEvent(rs, request);
            return;
        }
        try {
            syncSendEvent(rs, request, sourceExceptionHandler);
        } finally {
            mailbox.dispatchEvents();
            mailbox.sendPendingMessages();
            eventQueue.relinquishControl();
        }
    }

    /**
     * Process a request asynchronously.
     *
     * @param rs      The source of the request.
     * @param request The request.
     */
    private void asyncSendEvent(RequestSource rs, Request request) {
        JAEventRequest jaRequest = new JAEventRequest(rs, this, request, null);
        rs.send(mailbox, jaRequest);
    }

    /**
     * Process a request from another mailbox synchronously.
     *
     * @param rs                     The source of the request.
     * @param request                The request.
     * @param sourceExceptionHandler Exception handler of the source actor.
     */
    private void syncSendEvent(RequestSource rs,
                               Request request,
                               ExceptionHandler sourceExceptionHandler) {
        Mailbox oldSourceMailbox = rs.getMailbox();
        JARequest oldSourceRequest = oldSourceMailbox.getCurrentRequest();
        JAEventRequest jaRequest = new JAEventRequest(rs, this, request, null);
        mailbox.setCurrentRequest(jaRequest);
        try {
            _processRequest(request, JANoResponse.nrp);
        } catch (Exception ex) {
            mailbox.processResponse(jaRequest, ex);
        }
        oldSourceMailbox.setCurrentRequest(oldSourceRequest);
        oldSourceMailbox.setExceptionHandler(sourceExceptionHandler);
    }

    /**
     * Send a request to another actor.
     *
     * @param actor   The target actor.
     * @param request The request.
     * @param rp      The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    final protected void send(final Actor actor,
                              final Request request,
                              final RP rp)
            throws Exception {
        actor.acceptRequest(this, request, rp);
    }

    /**
     * Send a request to another actor and discard any response.
     *
     * @param actor   The target actor.
     * @param request The request.
     */
    final protected void sendEvent(Actor actor, Request request) {
        try {
            send(actor, request, JANoResponse.nrp);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Unexpected exception", ex);
        }
    }

    /**
     * Creates a _SMBuilder.
     */
    final public class SMBuilder extends _SMBuilder {
        @Override
        final public void send(Actor actor, Request request, RP rp)
                throws Exception {
            JLPCActor.this.send(actor, request, rp);
        }
    }

    /**
     * Returns the mailbox factory.
     *
     * @return The mailbox factory.
     */
    final public MailboxFactory getMailboxFactory() {
        return mailbox.getMailboxFactory();
    }

    /**
     * Returns true when the concurrent data of the actor, or its parent, contains the named data item.
     *
     * @param name The key for the data item.
     * @return True when the concurrent data of the actor, or its parent, contains the named data item.
     */
    @Override
    final public boolean hasDataItem(String name) {
        if (parent == null)
            return false;
        return parent.hasDataItem(name);
    }

    /**
     * The application method for processing requests sent to the actor.
     *
     * @param request A request.
     * @param rp      The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    private void _processRequest(Request request, RP rp)
            throws Exception {
        setExceptionHandler(null);
        request.processRequest(this, rp);
    }

    /**
     * The application method for processing requests sent to the actor.
     *
     * @param request A request.
     * @param rp      The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    @Deprecated
    protected void processRequest(Object request, RP rp)
            throws Exception {
        throw new UnsupportedOperationException(request.getClass().getName());
    }
}

final class JAEventRequest extends JARequest {
    public JAEventRequest(APCRequestSource requestSource,
                          RequestProcessor requestProcessor,
                          Request unwrappedRequest,
                          RP rp) {
        super(
                (RequestSource) requestSource,
                requestProcessor,
                unwrappedRequest,
                rp);
    }

    @Override
    public boolean isEvent() {
        return true;
    }

    @Override
    public void handleResponse(Object response) throws Exception {
    }
}
