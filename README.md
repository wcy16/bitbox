# BitBox

Project for comp90015 2019 semester 1.

## Server

The TCP server uses a non-blocking IO to communicate with other peers. The main
thread repeatedly check if a channel is ready to read, and if a channel is ready 
to read, the main thread will push the channel into a queue and lock this channel
until it has been read by a worker.

The UDP server monitor a single port and push every data received from that port 
to the queue and let the workers process the data.

Several worker threads monitoring the queue and execute the jobs in the queue 
continuously.

There is also a synchronize thread which will generate synchronize jobs every
_syncInterval_ seconds.

About timeout detection, there is a thread monitoring a delay queue. If a job 
want to add timeout detection, it add a _TimeoutJob_ to that delay queue. If 
a task (a job or a series of jobs) is finished within the given time, it will be removed from that queue.
If the task is not finished at that time, the timeout detection thread will execute the corresponding 
method to whether execute some job again or just interrupt some thread. Currently we have three kinds of 
TimeoutJobs:

+ _BlockingTimeoutJob:_ It shows how many times a thread can be blocked and will interrupt that
thread if the time runs out. Used in TCP mode.

+ _NoResponseTimeoutJob:_ It is used when a maximum response time is needed from other peers. 
The corresponding job will be sent to the queue again if maximum response time is reached.

+ _Pulse:_ A pulse is to detect if a UDP peer is still alive. We assume every a UDP package is received
from a peer, the peer pulses. If a peer does not pulse in a certain amount of time, the peer is 
treated as dead and the server will disconnect to it.

The _File System Observer_ provided by Aaron is monitoring the file system and
generate file event jobs when some modifications are made.

## Client

The client communicate with the server using AES128 Electronic Codebook (ECB) mode. 
The AES key is encrypted using client's public key stored in configuration file. A sample 
public key looks like:
    
    ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC+N1y1sovCtFw6X2XRWrAs5NfAhIV6UU/yDIyxuAwaWGxaJAeMJeiVv0Ax0imvpeN8pZsRqng6Wdo+mMd2gx8LrzDkjNHSZBDvhQ9VGejBbjMcTIt+UQqKU1QsyAd7oOLpeZrSnhRjN+HrDZc2k74ONMba21ufseiJD2dpl0+iu5u2o5xeR4sjuHxVVBJhJ0OeSzGSwIhYBk6VPfJLyZNX7COdKv4xrMts1Zg2TQ+VPHJAeODyyU2P1qd04dRouoQ6AihOxqqzI8Ye7H5+KRlS8GAw5HuLign8cnyLZ8CBHUPtehSix1ht928obEeafI8MOlwddBKmJiCp1SC+w3WB wcy@test

For the client side, the client private key is stored in a local file called "bitboxclient_rsa".
A sample private key looks like:

    -----BEGIN RSA PRIVATE KEY-----
    MIIEpAIBAAKCAQEAvjdctbKLwrRcOl9l0VqwLOTXwISFelFP8gyMsbgMGlhsWiQH
    jCXolb9AMdIpr6XjfKWbEap4OlnaPpjHdoMfC68w5IzR0mQQ74UPVRnowW4zHEyL
    flEKilNULMgHe6Di6Xma0p4UYzfh6w2XNpO+DjTG2ttbn7HoiQ9naZdPorubtqOc
    XkeLI7h8VVQSYSdDnksxksCIWAZOlT3yS8mTV+wjnSr+MazLbNWYNk0PlTxyQHjg
    8slNj9andOHUaLqEOgIoTsaqsyPGHux+fikZUvBgMOR7i4oJ/HJ8i2fAgR1D7XoU
    osdYbfdvKGxHmnyPDDpcHXQSpiYgqdUgvsN1gQIDAQABAoIBAQCyJp/J/QMwxENs
    2FRAE7PVGC+Ju5RXzzgU9vP+ruWG9zHj6sK22/pN5eV1w+QPAkthPqlRjls0K1tg
    LS8DLCMzik08gTcy2U2TRwfpKG76Wv+7jWVIVRaOHyVDG8UGYNSk3qPGhw+JLZmF
    0CDDmPPz0jI8YB4cH0AuviKYnoKTYYtO6DL2DkdkmsCUAdRvLZICreFN1VBlxVcW
    169c0g6xNXo9a+sLk4bNITn9M+BhnILLHX+EN1Y4/f51DR1T5+d/cP8RS608UVQX
    TK/RnlSWJDxhtsWctpWr/evFefAlZkiwELhEh/3SoEqJt6A5geuAVYQWe6SH7oDf
    Qat/sQABAoGBAPNIwFQrJOeS1a1dJOxQkwkueMtPXqmpmKyHkcZSqGu05AgnqVYY
    4ASrwQbmclRiuybVTrHWM953FpJY46vzVpCVxV73Q5JAnsz2E6q1jxEFNlctqGmC
    /jMCTyOkq0Cp4Ye2BCIhiP7bT/tYH4RCPEUg7vaSwlpdt4LMeYT4X4EBAoGBAMgo
    ie/e6iCxW9ah0rknefKZVFwav5XdMzSpmLoX2oVudoRzD89nbsIMAN5kzOlAstMa
    SNpfK5IHiFjUV15bYGaXAXwbW8vsMbk+mSs1mWOoVBM7LReKeOlNCZK11NmH9mRj
    Q6nYaTLXPuD2PpuZ6APXXu497+J51ZjixOqhL3SBAoGACm1u2Oy0ezx+7hxU7dAS
    TU1xnf076He9dH+nDuISF/O75mGUz3zndSvLbTlJYzaMIQD5i4PL21gtXn5y27bl
    WfMhb42XltgwNkbB1wpLJIadqqTpWARmUtdhfvya3n2pjgCOMsxPK+VIi1RenXOd
    U7UyqNzneaoUqIWNG9bteQECgYAEotu/EJy/sRv9drYYz89FTUPk9kGlyP/comkF
    NnQ7Tvmzy658EVtBZ2HFxPPyGyuJNDynwjiSI1aHKDP13Yv4FFtWcpPHv2rPbaHC
    nHU3F7kK0P4UY4K1dLFaEpghicKtRJdWocqeANpV/54noIL9Q8nRHuIljsjhx67j
    +GPPAQKBgQDyxGBD0emPfc6CBJXYUlKRB1lgzCUQiEyMUwDZM+r8EtIL6w46/bSw
    EvbWD8k1K54NNTXC6TrQ+bXti1JesMLXs7w+6vYDkdZ6IRIV7BM2H9ROpMbjsS8E
    5G2dVcSAdg0Yq8t/Zzpa3sw7pH15uryuXNFymPhzdeVpnblUl3QELw==
    -----END RSA PRIVATE KEY-----
    
The client support three commands:

1. listing peers

        java -cp bitbox.jar unimelb.bitbox.Client -c list_peers -s [server address] -i [client id]
    
2. connect to peer

        java -cp bitbox.jar unimelb.bitbox.Client -c connect_peer -s [server address] -p [peer address] -i [client id]
        
3. disconnect to peer

        java -cp bitbox.jar unimelb.bitbox.Client -c disconnect_peer -s [server address] -p [peer address] -i [client id]


## Project Structure

+ **Peer.java**: the bitbox peer
+ **ServerMain.java**: the main class for server
+ **PeerInfo.java**: store informations of connected peer
+ **Protocol.java**: store all of the protocols
+ **Worker.java**: workers processing the jobs
+ **ProtocolParsingException.java**: exception for parsing protocols
+ **TimeoutMonitor.java**: a monitor for timeout detection
+ **ClientListenThread**: thread listening to the client
+ **Client**: the bitbox client
+ **jobs/** : jobs for workers to execute.
    1. **Job.java**: Inherited by all the other jobs
    2. **JobStatus.java**: the return status of a job
    3. **RegisterTCPIn.java**: when other peers want to connect to the server in TCP mode
    4. **RegisterTCPOut.java**: when connecting to other peers in TCP mode
    5. **RegisterUDPIn.java**: when other peers want to connect to the server in UDP mode
    6. **RegisterUDPOut.java**: when connecting to other peers in UDP mode
    7. **ParseTCPRequest.java**: parse TCP requests
    8. **ParseUDPRequest.java**: parse UDP requests
    9. **InvalidRequest.java**: when processing an invalid request
    10. **FileEventRequest.java**: send file events to connected peers
    11. **FileBytesRequest.java**: send request for file bytes
    12. **Response.java**: process request response
    13. **TimeoutJob.java**: Inherited by all the other timeout jobs
    14. **BlockingTimeoutJob.java**: thread block timeout
    15. **NoResponseTimeoutJob.java**: response timeout
    16. **Pulse.java**: pulse (peer alive) timeout (in UDP mode)
+ **util/** : utilities for this project. (provided by Aaron)

## Configuration file

Since we have many new functions implemented, we some new values in configuration file.
Here are the values types and meanings. 

| value | type | meaning |
| --- | --- | --- |
| path | string | path to the share folder |
| port | int | the port the server is listening |
| advertisedName | string | the name of the peer |
| peers | string | the peers that the server want to connect to, comma seperated |
| maximumIncommingConnections | int | maximum incomming connection for the server |
| blockSize | int | block size for file read. (minimum of 8192 and blockSize in UDP mode ) |
| syncInterval | int | time interval (second) for synchronize event. |
| maxTries | int | max tries for a job before it fails |
| maxResponseTime | int | max response time (second) for a request |
| maxBlockTime | int | max wait time (second) for a blocking operation |
| maxPulseInterval | int | max interval (second) for a peer pulse |
| mode | string | either "tcp" or "udp" |
| clientPort | int | the port listening for client |
| authorized_keys | string | authorized client public keys |


