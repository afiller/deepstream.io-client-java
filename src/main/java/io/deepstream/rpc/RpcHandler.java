package io.deepstream.rpc;

import com.google.gson.JsonObject;
import io.deepstream.DeepstreamClient;
import io.deepstream.DeepstreamException;
import io.deepstream.IConnection;
import io.deepstream.constants.Actions;
import io.deepstream.constants.Topic;
import io.deepstream.message.Message;
import io.deepstream.message.MessageBuilder;
import io.deepstream.utils.AckTimeoutRegistry;
import io.deepstream.utils.ResubscribeNotifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RpcHandler {

    private Map options;
    private IConnection connection;
    private DeepstreamClient client;
    private Map<String, RpcCallback> providers;
    private AckTimeoutRegistry ackTimeoutRegistry;
    private ResubscribeNotifier resubscribeNotifier;
    private Map<String, Rpc> rpcs;

    public RpcHandler(Map options, IConnection connection, DeepstreamClient client ) {
        this.options = options;
        this.connection = connection;
        this.client = client;
        this.providers = new HashMap<>();
        this.rpcs = new HashMap<>();
        int timeoutDuration = (int) this.options.get( "subscriptionTimeout" );
        this.ackTimeoutRegistry = new AckTimeoutRegistry( this.client, Topic.RPC, timeoutDuration );
    }

    public void provide( String name, RpcCallback callback ) {
        if( this.providers.containsKey( name ) ) {
            throw new DeepstreamException( "RPC " + name + " already registered" );
        }

        this.ackTimeoutRegistry.add( name, Actions.SUBSCRIBE );
        this.providers.put( name, callback );
        this.connection.sendMsg( Topic.RPC, Actions.SUBSCRIBE, name );
    }

    public void unprovide( String name ) {
        if( this.providers.containsKey( name ) ) {
            this.providers.remove( name );
            this.ackTimeoutRegistry.add( name, Actions.UNSUBSCRIBE );
            this.connection.sendMsg( Topic.RPC, Actions.UNSUBSCRIBE, name );
        }
    }

    public void make(String name, JsonObject data, RpcResponseCallback callback ) {
        String uid = this.client.getUid();
        String typedData = MessageBuilder.typed( data );

        this.rpcs.put( uid, new Rpc( this.options, this.client, callback ) );
        this.connection.sendMsg( Topic.RPC, Actions.REQUEST, Arrays.asList( name, uid, typedData ) );
    }

    protected void handle( Message message ) {
        String rpcName, correlationId;
        Rpc rpc;

        // RPC Requests
        if( message.action == Actions.REQUEST ) {
            this.respondToRpc( message );
            return;
        }
        // RPC subscription Acks
        if( message.action == Actions.ACK &&
                ( message.data[ 0 ].equals( Actions.SUBSCRIBE.name() ) || message.data[ 0 ].equals( Actions.UNSUBSCRIBE.name() ) ) ) {
            this.ackTimeoutRegistry.clear( message );
        }

        /*
         * Error messages always have the error as first parameter. So the
         * order is different to ack and response messages
         */
        if( message.action == Actions.ERROR ) {
            rpcName = message.data[ 1 ];
            correlationId = message.data[ 2 ];
        } else {
            rpcName = message.data[ 0 ];
            correlationId = message.data[ 1 ];
        }

        /*
        * Retrieve the rpc object
        */
        rpc = this.getRpc( correlationId, rpcName, message.raw );
        if( rpc === null ) {
            return;
        }

        // RPC Responses
        if( message.action === C.ACTIONS.ACK ) {
            rpc.ack();
        }
        else if( message.action === C.ACTIONS.RESPONSE ) {
            rpc.respond( message.data[ 2 ] );
            delete this._rpcs[ correlationId ];
        }
        else if( message.action === C.ACTIONS.ERROR ) {
            message.processedError = true;
            rpc.error( message.data[ 0 ] );
            delete this._rpcs[ correlationId ];
        }
    }

    private Rpc getRpc(String correlationId, String rpcName, String raw) {
        gf
    }

    private void respondToRpc( Message message ) {
    }
}
