import jwt from 'jsonwebtoken'

const verifyToken = (req, res, next)=>{
    const token = req.cookies.jwt;

    if(!token){
        res.status(401).json({message: "Unauthorized token not provided"});
    }

    try {
        const decodedToken = jwt.verify(token, process.env.JWT_SECRET);
        next();
    } catch (error) {
        console.log(error.message);
    }
}

export default verifyToken;