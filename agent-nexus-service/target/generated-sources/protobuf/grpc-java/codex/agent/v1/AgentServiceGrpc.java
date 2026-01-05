package codex.agent.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: agent.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AgentServiceGrpc {

  private AgentServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "codex.agent.v1.AgentService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<codex.agent.v1.Agent.RunTaskRequest,
      codex.agent.v1.Agent.RunTaskResponse> getRunTaskMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RunTask",
      requestType = codex.agent.v1.Agent.RunTaskRequest.class,
      responseType = codex.agent.v1.Agent.RunTaskResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<codex.agent.v1.Agent.RunTaskRequest,
      codex.agent.v1.Agent.RunTaskResponse> getRunTaskMethod() {
    io.grpc.MethodDescriptor<codex.agent.v1.Agent.RunTaskRequest, codex.agent.v1.Agent.RunTaskResponse> getRunTaskMethod;
    if ((getRunTaskMethod = AgentServiceGrpc.getRunTaskMethod) == null) {
      synchronized (AgentServiceGrpc.class) {
        if ((getRunTaskMethod = AgentServiceGrpc.getRunTaskMethod) == null) {
          AgentServiceGrpc.getRunTaskMethod = getRunTaskMethod =
              io.grpc.MethodDescriptor.<codex.agent.v1.Agent.RunTaskRequest, codex.agent.v1.Agent.RunTaskResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RunTask"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  codex.agent.v1.Agent.RunTaskRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  codex.agent.v1.Agent.RunTaskResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AgentServiceMethodDescriptorSupplier("RunTask"))
              .build();
        }
      }
    }
    return getRunTaskMethod;
  }

  private static volatile io.grpc.MethodDescriptor<codex.agent.v1.Agent.ChatRequest,
      codex.agent.v1.Agent.ChatResponse> getRealtimeChatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RealtimeChat",
      requestType = codex.agent.v1.Agent.ChatRequest.class,
      responseType = codex.agent.v1.Agent.ChatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<codex.agent.v1.Agent.ChatRequest,
      codex.agent.v1.Agent.ChatResponse> getRealtimeChatMethod() {
    io.grpc.MethodDescriptor<codex.agent.v1.Agent.ChatRequest, codex.agent.v1.Agent.ChatResponse> getRealtimeChatMethod;
    if ((getRealtimeChatMethod = AgentServiceGrpc.getRealtimeChatMethod) == null) {
      synchronized (AgentServiceGrpc.class) {
        if ((getRealtimeChatMethod = AgentServiceGrpc.getRealtimeChatMethod) == null) {
          AgentServiceGrpc.getRealtimeChatMethod = getRealtimeChatMethod =
              io.grpc.MethodDescriptor.<codex.agent.v1.Agent.ChatRequest, codex.agent.v1.Agent.ChatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RealtimeChat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  codex.agent.v1.Agent.ChatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  codex.agent.v1.Agent.ChatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AgentServiceMethodDescriptorSupplier("RealtimeChat"))
              .build();
        }
      }
    }
    return getRealtimeChatMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AgentServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceStub>() {
        @java.lang.Override
        public AgentServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceStub(channel, callOptions);
        }
      };
    return AgentServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AgentServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceBlockingStub>() {
        @java.lang.Override
        public AgentServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceBlockingStub(channel, callOptions);
        }
      };
    return AgentServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AgentServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceFutureStub>() {
        @java.lang.Override
        public AgentServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceFutureStub(channel, callOptions);
        }
      };
    return AgentServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 执行一次代理任务
     * 输入：历史记录 + 新指令
     * 输出：执行结果 + 新增记录（流式）
     * </pre>
     */
    default void runTask(codex.agent.v1.Agent.RunTaskRequest request,
        io.grpc.stub.StreamObserver<codex.agent.v1.Agent.RunTaskResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRunTaskMethod(), responseObserver);
    }

    /**
     * <pre>
     * 全双工实时对话接口
     * 支持音频/文本流式输入，实时返回 STT 结果和 Agent 回复
     * </pre>
     */
    default io.grpc.stub.StreamObserver<codex.agent.v1.Agent.ChatRequest> realtimeChat(
        io.grpc.stub.StreamObserver<codex.agent.v1.Agent.ChatResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getRealtimeChatMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AgentService.
   */
  public static abstract class AgentServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AgentServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AgentService.
   */
  public static final class AgentServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AgentServiceStub> {
    private AgentServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 执行一次代理任务
     * 输入：历史记录 + 新指令
     * 输出：执行结果 + 新增记录（流式）
     * </pre>
     */
    public void runTask(codex.agent.v1.Agent.RunTaskRequest request,
        io.grpc.stub.StreamObserver<codex.agent.v1.Agent.RunTaskResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getRunTaskMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 全双工实时对话接口
     * 支持音频/文本流式输入，实时返回 STT 结果和 Agent 回复
     * </pre>
     */
    public io.grpc.stub.StreamObserver<codex.agent.v1.Agent.ChatRequest> realtimeChat(
        io.grpc.stub.StreamObserver<codex.agent.v1.Agent.ChatResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getRealtimeChatMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AgentService.
   */
  public static final class AgentServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AgentServiceBlockingStub> {
    private AgentServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 执行一次代理任务
     * 输入：历史记录 + 新指令
     * 输出：执行结果 + 新增记录（流式）
     * </pre>
     */
    public java.util.Iterator<codex.agent.v1.Agent.RunTaskResponse> runTask(
        codex.agent.v1.Agent.RunTaskRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getRunTaskMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AgentService.
   */
  public static final class AgentServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AgentServiceFutureStub> {
    private AgentServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_RUN_TASK = 0;
  private static final int METHODID_REALTIME_CHAT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_RUN_TASK:
          serviceImpl.runTask((codex.agent.v1.Agent.RunTaskRequest) request,
              (io.grpc.stub.StreamObserver<codex.agent.v1.Agent.RunTaskResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REALTIME_CHAT:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.realtimeChat(
              (io.grpc.stub.StreamObserver<codex.agent.v1.Agent.ChatResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRunTaskMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              codex.agent.v1.Agent.RunTaskRequest,
              codex.agent.v1.Agent.RunTaskResponse>(
                service, METHODID_RUN_TASK)))
        .addMethod(
          getRealtimeChatMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              codex.agent.v1.Agent.ChatRequest,
              codex.agent.v1.Agent.ChatResponse>(
                service, METHODID_REALTIME_CHAT)))
        .build();
  }

  private static abstract class AgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AgentServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return codex.agent.v1.Agent.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AgentService");
    }
  }

  private static final class AgentServiceFileDescriptorSupplier
      extends AgentServiceBaseDescriptorSupplier {
    AgentServiceFileDescriptorSupplier() {}
  }

  private static final class AgentServiceMethodDescriptorSupplier
      extends AgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AgentServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AgentServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AgentServiceFileDescriptorSupplier())
              .addMethod(getRunTaskMethod())
              .addMethod(getRealtimeChatMethod())
              .build();
        }
      }
    }
    return result;
  }
}
