package ca.nakednate.game;

import ca.nakednate.game.android.AndroidService;
import ca.nakednate.game.models.GameState;
import ca.nakednate.game.models.PlayerInfo;
import ca.nakednate.game.models.events.GameRequestEvent;
import ca.nakednate.game.models.events.NewPlayerEvent;
import ca.nakednate.game.models.events.RequestPlayerInfoEvent;
import ca.nakednate.game.p2p.ClientHandler;
import ca.nakednate.game.p2p.ClientManager;
import ca.nakednate.game.p2p.MessageHandler;
import ca.nakednate.game.p2p.Peer;
import ca.nakednate.game.p2p.listeners.DiscoveryServiceListener;
import ca.nakednate.game.p2p.listeners.MainScreenListener;
import ca.nakednate.game.p2p.listeners.PeerDiscoveryListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;


public class MainMenuScreen extends BaseScreen implements PeerDiscoveryListener, MainScreenListener {

    private static final String LOG_TAG = MainMenuScreen.class.getSimpleName();
    List<ClientHandler> mClientHandlerListView;
    TextField mDisplayNameTextField;
    GameRequestEvent mSentRequest = null;

    private static DiscoveryServiceListener mDiscoveryServiceListener;

    public MainMenuScreen(final UnfriendlyFire game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Label nameLabel = new Label("Name: ", getSkin());
        Label gameListLabel = new Label("Available Games:", getSkin());


        String displayName = getDisplayName();
        mDisplayNameTextField = new TextField(displayName, getSkin());
        mDisplayNameTextField.setTextFieldListener(new TextField.TextFieldListener() {
            @Override
            public void keyTyped(TextField textField, char c) {
                saveDisplayName(mDisplayNameTextField.getText());
                notifyClientHandlersOfNameChange();
            }
        });

        TextButton refreshButton = new TextButton("Refresh", getSkin());
        refreshButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (mDiscoveryServiceListener == null) {
                    Gdx.app.error(LOG_TAG, "DiscoveryServiceListener not set");
                    return;
                }

                mDiscoveryServiceListener.onDiscoveryRefresh();
            }
        });

        mClientHandlerListView = new List<ClientHandler>(getSkin());
        setClientHandlers(new java.util.ArrayList<ClientHandler>());
        mClientHandlerListView.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                super.clicked(event, x, y);

                ClientHandler selectedOpponent = mClientHandlerListView.getSelected();
                GameRequestEvent gameRequestEvent = new GameRequestEvent(   true);
                selectedOpponent.sendJson(GameRequestEvent.class, gameRequestEvent.toJSON());

                mSentRequest = gameRequestEvent;
                // Set message originator so we remember who we asked
                mSentRequest.setMessageOriginator(selectedOpponent);
            }
        });
        ScrollPane scrollPane = new ScrollPane(mClientHandlerListView, getSkin());

        Table table = new Table(getSkin());
        table.align(Align.top);
        table.setBounds(Gdx.graphics.getWidth() * 0.05f, 10, Gdx.graphics.getWidth() * 0.9f,
                Gdx.graphics.getHeight() * 0.55f);
        table.add(nameLabel);
        table.add(mDisplayNameTextField).width(Gdx.graphics.getWidth() * 0.3f);
        table.row();
        table.add(gameListLabel).colspan(2);
        table.row();
        table.add(scrollPane).width(Gdx.graphics.getWidth() * 0.5f).colspan(2);
        table.row();
        table.add(refreshButton).colspan(2).center();

        Image background = new Image(new TextureRegion(new Texture(Gdx.files.internal("skin/title_screen.png"))));
        background.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        getStage().addActor(background);
        getStage().addActor(table);

        MessageHandler.setMainScreenListener(this);
        ClientManager.setMainScreenListener(this);
    }

    @Override
    public void render(float delta) {
        super.render(delta);

        int numberClientsInList = mClientHandlerListView.getItems().size;
        int numberClientHandlers = ClientManager.getClientHandlers().size();
        if(numberClientsInList != numberClientHandlers) {
            syncClientHandlerListView();
        }
    }

    @Override
    public void hide() {
        MessageHandler.setMainScreenListener(null);
        ClientManager.setMainScreenListener(null);
    }

    @Override
    public void onPeersDiscovered(java.util.List<Peer> peers) {

        for (Peer peer : peers) {

            ClientHandler clientHandler = ClientManager.getOrCreateClientHandler(peer);
            if (clientHandler == null) {
                Gdx.app.error(LOG_TAG, "Unable to establish a socket connection with clientHandler: " + clientHandler);
                return;
            }

            if (clientHandler.getPeer().getDisplayName() == null) {
                handshake(clientHandler);
            }
        }
    }

    public String getDisplayName() {
        String phoneName = AndroidService.getPhoneName();
        String displayName = AndroidService.onLoadSharedPref(AndroidService.DISPLAY_NAME, phoneName);
        return displayName;
    }

    public void saveDisplayName(String displayName) {
        AndroidService.onSaveSharedPref(AndroidService.DISPLAY_NAME, displayName);
    }

    /**
     * Sets up a client handler/socket with the peer if we don't have one.
     * Sends them our name
     *
     * @param clientHandler
     */
    private void handshake(ClientHandler clientHandler) {
        sendDisplayNameToClient(clientHandler);
    }

    /**
     * Tell this client of my name
     *
     * @param clientHandler
     */
    private void sendDisplayNameToClient(ClientHandler clientHandler) {
        try {
            PlayerInfo playerInfo = new PlayerInfo(mDisplayNameTextField.getText());
            NewPlayerEvent newPlayerEvent = new NewPlayerEvent(playerInfo);

            clientHandler.sendJson(NewPlayerEvent.class, newPlayerEvent.toJSON());
        } catch (Exception e) {
            Gdx.app.log(LOG_TAG, "Failed to send display name to client: " + clientHandler);
            e.printStackTrace();
        }
    }

    /**
     * Tell all clients about my new name
     */
    private void notifyClientHandlersOfNameChange() {
        for (ClientHandler clientHandler : ClientManager.getClientHandlers()) {
            sendDisplayNameToClient(clientHandler);
        }
    }

    /**
     * Update UI client handler list
     *
     * @param clientHandlerList
     */
    public void setClientHandlers(java.util.List<ClientHandler> clientHandlerList) {
        Array<ClientHandler> array = new Array<ClientHandler>(clientHandlerList.toArray(new ClientHandler[clientHandlerList.size()]));
        mClientHandlerListView.setItems(array);
    }

    @Override
    public void onRequestPlayerInfoEvent(RequestPlayerInfoEvent requestPlayerInfoEvent) {
        ClientHandler clientHandler = requestPlayerInfoEvent.getMessageOriginator();
        sendDisplayNameToClient(clientHandler);
    }

    @Override
    public void onNewPlayerRecieved(NewPlayerEvent newPlayerEvent) {
        Peer peer = newPlayerEvent.getMessageOriginator().getPeer();
        peer.setDisplayName(newPlayerEvent.getPlayerInfo().getDisplayName());

        syncClientHandlerListView();
    }

    private void syncClientHandlerListView() {
        java.util.List<ClientHandler> clientHandlers = new ArrayList<ClientHandler>(ClientManager.getClientHandlers());

        for(ClientHandler clientHandler : clientHandlers) {
            if(clientHandler.getPeer().getDisplayName() == null) {
                RequestPlayerInfoEvent requestPlayerInfoEvent = new RequestPlayerInfoEvent();
                clientHandler.sendJson(RequestPlayerInfoEvent.class, requestPlayerInfoEvent.toJSON());
            }
        }

        setClientHandlers(clientHandlers);
    }

    @Override
    public void onNewClientAdded() {
        syncClientHandlerListView();
    }

    @Override
    public void onGameRequestEvent(GameRequestEvent incomingGameRequestEvent) {
        ClientHandler opponent = incomingGameRequestEvent.getMessageOriginator();

        if(mSentRequest != null && mSentRequest.getMessageOriginator() == opponent) {
            if(incomingGameRequestEvent.isAccepted()) {
                startGame(opponent);
            }
            mSentRequest = null;
            return;
        }

        promptJoinGame(opponent);
    }

    public void startGame(final ClientHandler opponent) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                GameState.getInstance().setOpponent(opponent);
                getGame().setScreen(new VehicleSelectionScreen(getGame()));
            }
        });
    }

    public void promptJoinGame(final ClientHandler opponent) {
        new Dialog("Some Dialog", getSkin(), "dialog") {
            protected void result (Object object) {
                System.out.println("Chosen: " + object);
                acceptGameRequest(opponent, (Boolean) object);
            }
        }.text("Accept game request from " + opponent.getPeer().getDisplayName() + "?").button("Yes", true).button("No", false).key(Input.Keys.ENTER, true)
                .key(Input.Keys.ESCAPE, false).show(getStage());
    }

    private void acceptGameRequest(ClientHandler opponent, boolean accepted) {
        GameRequestEvent outgoingGameRequestEvent = new GameRequestEvent(accepted);
        opponent.sendJson(GameRequestEvent.class, outgoingGameRequestEvent.toJSON());

        if(accepted) {
            startGame(opponent);
        }
    }

    public static void setDiscoveryServiceListener(DiscoveryServiceListener discoveryServiceListener) {
        mDiscoveryServiceListener = discoveryServiceListener;
    }
}
