type Props = {title:string, description:string, icon?:string}
export default function FeatureCard({title, description, icon}:Props){
  return (
    <article className="feature-card">
      <div className="feature-icon">{icon}</div>
      <h3>{title}</h3>
      <p>{description}</p>
    </article>
  )
}
