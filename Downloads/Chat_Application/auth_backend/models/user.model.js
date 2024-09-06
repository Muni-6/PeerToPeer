import mongoose, { Schema } from "mongoose";

const userSchema = new Schema({
    username:{
        type: String,
        unique: true,
        required: true

    },
    password:{
        type: String,
        unique: true,
        required: true
    }
})

const userModel = mongoose.model('User', userSchema);

export default userModel;