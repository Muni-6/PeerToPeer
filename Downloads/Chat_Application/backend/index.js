import express from "express"
import dotenv from "dotenv"
import {Server} from "socket.io"
import http from "http"
 
 dotenv.config();
 const port = process.env.PORT || 5000;
 
 const app = express();
 const server = http.createServer(app);
 const io = new Server(server,{
   cors: {
      allowedHeaders: ["*"],
      origin: "*"
    }
 });

 io.on('connection', (socket) => {
    console.log(`Client connected`);
    const username = socket.handshake.query.userName;
    console.log('Username: ',username);
    socket.on('chat msg', (msg)=>{

      console.log(`Text Msg ` + msg.textMsg);
      console.log(`Recieved Msg ` + msg.receiver);
      console.log(`Sender Msg ` + msg.sender);
     //socket.broadcast.emit('chat msg', msg)
    });
 });
 app.get('/', (req,res)=>{
   res.send("Hi Welcome to HHLD");
 })
 //start server
 server.listen(port, ()=>{
    console.log(`server listening at http://localhost:${port}`);
 });