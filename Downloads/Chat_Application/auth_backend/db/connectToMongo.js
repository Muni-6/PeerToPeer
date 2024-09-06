import mongoose from "mongoose";
import dotenv from "dotenv";
const connectToMongo = async() => {
    try {
        await mongoose.connect(process.env.MONGO_URI);
        console.log(`Connected to MongoDB`);
    } catch (error) {
        console.log(`Error in connecting to mongoDB`, error.message);
    }
}

export default connectToMongo