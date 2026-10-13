import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell,
} from "recharts";
const colors = ["#9c8aff", "#5ccab0"];
export default function ActivityChart({
  data,
}: {
  data: { day: string; count: number }[];
}) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={data}>
        <XAxis
          dataKey="day"
          axisLine={false}
          tickLine={false}
          tick={{ fill: "#8791a5", fontSize: 12 }}
        />
        <YAxis
          allowDecimals={false}
          axisLine={false}
          tickLine={false}
          tick={{ fill: "#8791a5", fontSize: 12 }}
        />
        <Tooltip
          contentStyle={{
            background: "#171e2a",
            border: "1px solid #343c4d",
            color: "#eee",
            borderRadius: 8,
          }}
        />
        <Bar dataKey="count" name="표본 커밋" radius={[5, 5, 0, 0]}>
          {data.map((_, i) => (
            <Cell fill={colors[i % 2]} key={i} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
