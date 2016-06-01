package main

import (
	"fmt"
	"net/http"
	"github.com/urfave/negroni"
	"github.com/palantir/stacktrace"
	"encoding/json"
	"errors"
)

func main() {
	mux := http.NewServeMux()

	// no failure
	mux.HandleFunc("/ok", func(w http.ResponseWriter, req *http.Request) {
		value := "hello, world!"
		writeJSON(w, value, http.StatusOK)
	})

	// single error propagated with message
	mux.HandleFunc("/simpleStackTraceError", func(w http.ResponseWriter, req *http.Request) {
		err := simpleStackTraceError()
		writeJSON(w, err, http.StatusInternalServerError)
	})

	// error propagated 2 levels with messages at both
	mux.HandleFunc("/nestedStackTraceError", func(w http.ResponseWriter, req *http.Request) {
		err := outerStackTraceError()
		writeJSON(w, err, http.StatusInternalServerError)
	})

	// error propagated 2 levels with messages at neither
	mux.HandleFunc("/nestedNoMessageStackTraceError", func(w http.ResponseWriter, req *http.Request) {
		err := outerNoMessageStackTraceError()
		writeJSON(w, err, http.StatusInternalServerError)
	})

	// error propagated 2 levels -- inner error is returned from interface function with message,
	// outer error is propagated with no message
	mux.HandleFunc("/interfaceFunctionStackTraceError", func(w http.ResponseWriter, req *http.Request) {
		err := interfaceFunctionStackTraceError()
		writeJSON(w, err, http.StatusInternalServerError)
	})

	// standard Golang error returned directly
	mux.HandleFunc("/nativeError", func(w http.ResponseWriter, req *http.Request) {
		err := nativeError()
		writeJSON(w, err, http.StatusInternalServerError)
	})

	// panic handled by default negroni handler
	mux.HandleFunc("/panic", func(w http.ResponseWriter, req *http.Request) {
		panic("panicking in the server")
	})

	n := negroni.Classic()
	n.UseHandler(mux)

	http.ListenAndServe(":3000", n)
}

func simpleStackTraceError() error {
	err := errors.New("errors.New error message")
	return stacktrace.Propagate(err, "simpleError error message")
}

func outerStackTraceError() error {
	err := simpleStackTraceError()
	return stacktrace.Propagate(err, "outerError error message")
}

func noMessageStackTraceError() error {
	err := errors.New("errors.New error message")
	return stacktrace.Propagate(err, "")
}

func outerNoMessageStackTraceError() error {
	err := noMessageStackTraceError()
	return stacktrace.Propagate(err, "")
}

func interfaceFunctionStackTraceError() error {
	testInterface := TestInterface("foo")
	return stacktrace.Propagate(testInterface.interfaceError(), "")
}

func nativeError() error {
	return errors.New("errors.New error message")
}

func writeJSON(w http.ResponseWriter, obj interface{}, status int) {
	if err, ok := obj.(error); ok {
		fmt.Println("inside error")
		obj = err.Error()
	}
	data, err := json.Marshal(obj)
	if err != nil {
		fmt.Println("inside error 2")
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_, err = w.Write(data)
	if err != nil {
		// cannot report to client due to socket failure
		fmt.Errorf("Failed to write response %v to the caller", string(data))
	}
}

type TestInterface string

func (t TestInterface) interfaceError() error {
	err := errors.New("errors.New error message")
	return stacktrace.Propagate(err, "interface method error")
}
