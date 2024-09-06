import userModel from "../models/user.model.js";
import bcrypt from "bcrypt";
import generateJWTTokenAndSetCookie from "../utils/generateToken.js";
const signup = async(req, res) => {
    try {
        const {userName, password} = req.body;
        const hashPassword = await bcrypt.hash(password, 10); 
        const foundUser = await userModel.findOne({username:userName});
        console.log(userName);
        console.log(foundUser);

        if(foundUser){
            res.status(201).json({message: "User name already exits"});
        }else{
            //console.log(userName);
            //console.log(hashPassword);
            const user = new userModel({username: userName, password: hashPassword});
            //console.log(user);
            await user.save();
            generateJWTTokenAndSetCookie(user._id, res);
            res.status(201).json({message: "User registered"});
        }

    } catch (error) {
        console.log(error);
        res.status(501).json({message: "User registration failed"});
    }
}

export const login = async(req, res) => {
    try {
        const {userName, password} = req.body;
        //const hashPassword = await bcrypt.hash(password, 10); 
        const foundUser = await userModel.findOne({username:userName});

        if(!foundUser){
            res.status(401).json({message: "Auth failed"});
        }else{
            const passwordMatch = await bcrypt.compare(password, foundUser?.password);
            if(!passwordMatch){
                res.status(401).json({message: "Auth failed"});
            }
            generateJWTTokenAndSetCookie(foundUser._id, res);
            res.status(201).json({_id:foundUser._id, userName: foundUser.username});
        }

    } catch (error) {
        console.log(error);
        res.status(501).json({message: "Login failed"});
    }
}
 
export default signup;