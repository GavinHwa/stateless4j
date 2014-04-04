package com.github.oxo42.stateless;

import com.github.oxo42.stateless.delegates.Action1;
import com.github.oxo42.stateless.delegates.Action2;
import com.github.oxo42.stateless.delegates.Func;
import com.github.oxo42.stateless.delegates.Func2;
import com.github.oxo42.stateless.transitions.Transition;
import com.github.oxo42.stateless.transitions.TransitioningTriggerBehaviour;
import com.github.oxo42.stateless.triggers.*;
import com.github.oxo42.stateless.validation.Enforce;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Models behaviour as transitions between a finite set of states
 *
 * @param <TState>   The type used to represent the states
 * @param <TTrigger> The type used to represent the triggers that cause state transitions
 */
public class StateMachine<TState, TTrigger> {
    private final Map<TState, StateRepresentation<TState, TTrigger>> stateConfiguration = new HashMap<>();
    private final Map<TTrigger, TriggerWithParameters<TState, TTrigger>> triggerConfiguration = new HashMap<>();
    private final Func<TState> stateAccessor;
    private final Action1<TState> stateMutator;
    private Action2<TState, TTrigger> unhandledTriggerAction = new Action2<TState, TTrigger>() {

        public void doIt(TState state, TTrigger trigger) throws Exception {
            throw new Exception(
                    String.format(
                            "No valid leaving transitions are permitted from state '%s' for trigger '%s'. Consider ignoring the trigger.",
                            state, trigger)
            );
        }

    };

    /**
     * Construct a state machine
     *
     * @param initialState The initial state
     */
    public StateMachine(TState initialState) {
        final StateReference<TState, TTrigger> reference = new StateReference<>();
        reference.setState(initialState);
        stateAccessor = new Func<TState>() {
            public TState call() {
                return reference.getState();
            }
        };
        stateMutator = new Action1<TState>() {
            public void doIt(TState s) {
                reference.setState(s);
            }
        };
    }


    /**
     * The current state
     *
     * @return The current state
     */
    public TState getState() throws Exception {
        return stateAccessor.call();
    }

    private void setState(TState value) throws Exception {
        stateMutator.doIt(value);
    }

    /**
     * The currently-permissible trigger values
     *
     * @return The currently-permissible trigger values
     */
    public List<TTrigger> getPermittedTriggers() throws Exception {
        return getCurrentRepresentation().getPermittedTriggers();
    }

    StateRepresentation<TState, TTrigger> getCurrentRepresentation() throws Exception {
        return getRepresentation(getState());
    }

    StateRepresentation<TState, TTrigger> getRepresentation(TState state) {

        if (!stateConfiguration.containsKey(state)) {
            StateRepresentation<TState, TTrigger> result = new StateRepresentation<>(state);
            stateConfiguration.put(state, result);
        }

        return stateConfiguration.get(state);
    }


    /**
     * Begin configuration of the entry/exit actions and allowed transitions
     * when the state machine is in a particular state
     *
     * @param state The state to configure
     * @return A configuration object through which the state can be configured
     */
    public StateConfiguration<TState, TTrigger> configure(TState state) throws Exception {
        return new StateConfiguration<>(getRepresentation(state), new Func2<TState, StateRepresentation<TState, TTrigger>>() {

            public StateRepresentation<TState, TTrigger> call(TState arg0) {
                return getRepresentation(arg0);
            }
        });
    }


    /**
     * Transition from the current state via the specified trigger.
     * The target state is determined by the configuration of the current state.
     * Actions associated with leaving the current state and entering the new one
     * will be invoked
     *
     * @param trigger The trigger to fire
     * @throws Exception The current state does not allow the trigger to be fired
     */
    public void fire(TTrigger trigger) throws Exception {
        publicFire(trigger);
    }


    /**
     * Transition from the current state via the specified trigger.
     * The target state is determined by the configuration of the current state.
     * Actions associated with leaving the current state and entering the new one
     * will be invoked.
     *
     * @param trigger The trigger to fire
     * @param arg0    The first argument
     * @param <TArg0> Type of the first trigger argument
     * @throws Exception The current state does not allow the trigger to be fired
     */
    public <TArg0> void fire(TriggerWithParameters1<TArg0, TState, TTrigger> trigger, TArg0 arg0) throws Exception {
        Enforce.argumentNotNull(trigger, "trigger");
        publicFire(trigger.getTrigger(), arg0);
    }


    /**
     * Transition from the current state via the specified trigger.
     * The target state is determined by the configuration of the current state.
     * Actions associated with leaving the current state and entering the new one
     * will be invoked.
     *
     * @param trigger The trigger to fire
     * @param arg0    The first argument
     * @param arg1    The second argument
     * @param <TArg0> Type of the first trigger argument
     * @param <TArg1> Type of the second trigger argument
     * @throws Exception The current state does not allow the trigger to be fired
     */
    public <TArg0, TArg1> void fire(TriggerWithParameters2<TArg0, TArg1, TState, TTrigger> trigger, TArg0 arg0, TArg1 arg1) throws Exception {
        Enforce.argumentNotNull(trigger, "trigger");
        publicFire(trigger.getTrigger(), arg0, arg1);
    }


    /**
     * Transition from the current state via the specified trigger.
     * The target state is determined by the configuration of the current state.
     * Actions associated with leaving the current state and entering the new one
     * will be invoked.
     *
     * @param trigger The trigger to fire
     * @param arg0    The first argument
     * @param arg1    The second argument
     * @param arg2    The third argument
     * @param <TArg0> Type of the first trigger argument
     * @param <TArg1> Type of the second trigger argument
     * @param <TArg2> Type of the third trigger argument
     * @throws Exception The current state does not allow the trigger to be fired
     */
    public <TArg0, TArg1, TArg2> void fire(TriggerWithParameters3<TArg0, TArg1, TArg2, TState, TTrigger> trigger, TArg0 arg0, TArg1 arg1, TArg2 arg2) throws Exception {
        Enforce.argumentNotNull(trigger, "trigger");
        publicFire(trigger.getTrigger(), arg0, arg1, arg2);
    }

    void publicFire(TTrigger trigger, Object... args) throws Exception {
        TriggerWithParameters<TState, TTrigger> configuration;
        if (triggerConfiguration.containsKey(trigger)) {
            configuration = triggerConfiguration.get(trigger);
            configuration.validateParameters(args);
        }

        TriggerBehaviour<TState, TTrigger> triggerBehaviour;
        try {
            triggerBehaviour = getCurrentRepresentation().tryFindHandler(trigger);
        } catch (Exception e) {
            unhandledTriggerAction.doIt(getCurrentRepresentation().getUnderlyingState(), trigger);
            return;
        }

        TState source = getState();
        TState destination;
        try {
            destination = triggerBehaviour.resultsInTransitionFrom(source, args);
            Transition<TState, TTrigger> transition = new Transition<>(source, destination, trigger);

            getCurrentRepresentation().exit(transition);
            setState(transition.getDestination());
            getCurrentRepresentation().enter(transition, args);

        } catch (Exception e) {

        }
    }


    /**
     * Override the default behaviour of throwing an exception when an unhandled trigger is fired
     *
     * @param unhandledTriggerAction An action to call when an unhandled trigger is fired
     */
    public void onUnhandledTrigger(Action2<TState, TTrigger> unhandledTriggerAction) throws Exception {
        if (unhandledTriggerAction == null) {
            throw new Exception("unhandledTriggerAction");
        }
        this.unhandledTriggerAction = unhandledTriggerAction;
    }


    /**
     * Determine if the state machine is in the supplied state
     *
     * @param state The state to test for
     * @return True if the current state is equal to, or a substate of, the supplied state
     * @throws Exception
     */
    public Boolean isInState(TState state) throws Exception {
        return getCurrentRepresentation().isIncludedIn(state);
    }


    /**
     * Returns true if {@code trigger} can be fired  in the current state
     *
     * @param trigger Trigger to test
     * @return True if the trigger can be fired, false otherwise
     */
    public Boolean canFire(TTrigger trigger) throws Exception {
        return getCurrentRepresentation().canHandle(trigger);
    }


    /**
     * A human-readable representation of the state machine
     *
     * @return A description of the current state and permitted triggers
     */
    public String toString() {
        try {
            List<TTrigger> permittedTriggers = getPermittedTriggers();
            List<String> parameters = new ArrayList<>();

            for (TTrigger tTrigger : permittedTriggers) {
                parameters.add(tTrigger.toString());
            }

            StringBuilder params = new StringBuilder();
            String delim = "";
            for (String param : parameters) {
                params.append(delim);
                params.append(param);
                delim = ", ";
            }


            return String.format(
                    "StateMachine {{ State = %s, PermittedTriggers = {{ %s }}}}",
                    getState(),
                    params.toString());
        } catch (Exception e) {
            // I'm not sure if this is the best thing to do but don't have any better ideas
            // J. Oxley 2014/04/04
            throw new RuntimeException(e);
        }
    }


    /**
     * Specify the arguments that must be supplied when a specific trigger is fired
     *
     * @param trigger The underlying trigger value
     * @param classe0 Class argument
     * @param <TArg0> Type of the first trigger argument
     * @return An object that can be passed to the fire() method in order to fire the parameterised trigger
     */
    public <TArg0> TriggerWithParameters1<TArg0, TState, TTrigger> setTriggerParameters(TTrigger trigger, Class<TArg0> classe0) throws Exception {
        TriggerWithParameters1<TArg0, TState, TTrigger> configuration = new TriggerWithParameters1<>(trigger, classe0);
        saveTriggerConfiguration(configuration);
        return configuration;
    }


    /**
     * Specify the arguments that must be supplied when a specific trigger is fired
     *
     * @param trigger The underlying trigger value
     * @param classe0 Class argument
     * @param classe1 Class argument
     * @param <TArg0> Type of the first trigger argument
     * @param <TArg1> Type of the second trigger argument
     * @return An object that can be passed to the fire() method in order to fire the parameterised trigger
     */
    public <TArg0, TArg1> TriggerWithParameters2<TArg0, TArg1, TState, TTrigger> setTriggerParameters(TTrigger trigger, Class<TArg0> classe0, Class<TArg1> classe1) throws Exception {
        TriggerWithParameters2<TArg0, TArg1, TState, TTrigger> configuration = new TriggerWithParameters2<>(trigger, classe0, classe1);
        saveTriggerConfiguration(configuration);
        return configuration;
    }


    /**
     * Specify the arguments that must be supplied when a specific trigger is fired
     *
     * @param trigger The underlying trigger value
     * @param classe0 Class argument
     * @param classe1 Class argument
     * @param classe2 Class argument
     * @param <TArg0> Type of the first trigger argument
     * @param <TArg1> Type of the second trigger argument
     * @param <TArg2> Type of the third trigger argument
     * @return An object that can be passed to the fire() method in order to fire the parameterised trigger
     */
    public <TArg0, TArg1, TArg2> TriggerWithParameters3<TArg0, TArg1, TArg2, TState, TTrigger> setTriggerParameters(TTrigger trigger, Class<TArg0> classe0, Class<TArg1> classe1, Class<TArg2> classe2) throws Exception {
        TriggerWithParameters3<TArg0, TArg1, TArg2, TState, TTrigger> configuration = new TriggerWithParameters3<>(trigger, classe0, classe1, classe2);
        saveTriggerConfiguration(configuration);
        return configuration;
    }

    private void saveTriggerConfiguration(TriggerWithParameters<TState, TTrigger> trigger) throws Exception {
        if (triggerConfiguration.containsKey(trigger.getTrigger())) {
            throw new Exception(
                    String.format("Parameters for the trigger '%s' have already been configured.", trigger));
        }

        triggerConfiguration.put(trigger.getTrigger(), trigger);
    }

    public void generateDotFileInto(OutputStream dotFile) throws Exception {
        OutputStreamWriter w = new OutputStreamWriter(dotFile, "UTF-8");
        PrintWriter writer = new PrintWriter(w);
        writer.write("digraph G {\n");
        for (Entry<TState, StateRepresentation<TState, TTrigger>> entry : this.stateConfiguration.entrySet()) {
            Map<TTrigger, List<TriggerBehaviour<TState, TTrigger>>> behaviours = entry.getValue().getTriggerBehaviours();
            for (Entry<TTrigger, List<TriggerBehaviour<TState, TTrigger>>> behaviour : behaviours.entrySet()) {
                for (TriggerBehaviour<TState, TTrigger> triggerBehaviour : behaviour.getValue()) {
                    if (triggerBehaviour instanceof TransitioningTriggerBehaviour) {
                        writer.write(String.format("\t%s -> %s;\n", entry.getKey(), triggerBehaviour.resultsInTransitionFrom(null)));
                    }
                }
            }
        }
        writer.write("}");
        writer.close();
    }
}