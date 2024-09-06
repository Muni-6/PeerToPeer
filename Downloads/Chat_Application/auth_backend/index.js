import express from "express"
import dotenv from "dotenv"
import http from "http"
import authRouter from "./routes/auth.route.js";
import userRouter from "./routes/user.route.js";
import connectToMongo from "./db/connectToMongo.js";
import cookieParser from "cookie-parser";
 import cors from "cors"
 dotenv.config();
 const port = process.env.PORT || 5000;
 
const app = express();
app.use(cookieParser());
app.use(cors({
   credentials: true,
   origin: "http://localhost:3000"
}));
app.use(express.json());

app.use('/auth', authRouter);
app.use('/users', userRouter);
app.get('/', (req,res)=>{
   res.send("Hi Welcome to HHLD");
 })
 //start server
 app.listen(port, ()=>{
    console.log(`server listening at http://localhost:8081`);
    connectToMongo();
 });